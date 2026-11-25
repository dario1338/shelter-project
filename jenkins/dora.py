#!/usr/bin/env python3
"""Delivery metrics (inspired by DORA) computed from the history of the Jenkins CD jobs.

Read-only: nothing in the pipelines changes. The script asks the Jenkins JSON API for the
builds of the delivery jobs, reads the commit time from the local git clone and prints
markdown tables per component.

Usage (run it inside a clone of the repository, so that commit times can be read):

    python jenkins/dora.py                      # all components
    python jenkins/dora.py --since 2026-10-07   # only builds from this date on
    python jenkins/dora.py --csv builds.csv     # also write one row per build

Settings come from environment variables, never from arguments (they would end up in the
shell history):

    JENKINS_URL    default https://shelter-jenkins.duckdns.org
    JENKINS_USER   optional, needed only when anonymous reading is disabled
    JENKINS_TOKEN  optional, a Jenkins API token of that user

What the numbers mean, and what they do not, is explained in the thesis. In short: the sample
is small, there is one developer and the free hosting plan adds cold starts, so the values show
that delivery is measurable, they are not a rating of a team.

What is measured:
- a release is a build whose production stage succeeded;
- a failed change is a release that the pipeline itself rolled back after a failed check on
  production (its log ends with the rollback message); a failure found later by a person is not
  seen here;
- the time to restore runs from the failed check to the previous version serving again, as the
  pipeline records it (restore-event-*.json, archived by the build); older builds have no such record;
- the lead time is counted once per commit, from the commit on main to the end of its first release.
"""

import argparse
import base64
import csv
import json
import os
import statistics
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

JOBS = {
    'api': 'shelter-cd',
    'ui': 'shelter-cd-ui',
    'proxy': 'shelter-cd-proxy',
}

# Outcome of one build, from the point of view of delivery.
RELEASED = 'released'            # reached production and stayed there
ROLLED_BACK = 'rolled_back'      # production check failed, the previous version was restored
ROLLBACK_FAILED = 'rollback_failed'  # production check failed and the previous version was not restored
TEST_ROLLBACK = 'test_rollback'  # rollback exercised on purpose (TEST_ROLLBACK=true)
PIPELINE_FAILED = 'pipeline_failed'  # production stage failed before it changed production
STOPPED_EARLY = 'stopped_early'  # failed or was stopped before production (staging)
NOT_APPROVED = 'not_approved'    # rejected or timed out at the approval
FAILED_UNKNOWN = 'failed_unknown'  # failed, and no stage data tells whether it was before or after production
NO_PARAMS = 'no_params'          # started by hand without a commit, fails validation at once
INVALID_PARAMS = 'invalid_params'  # parameters the pipeline rejects before doing anything
RUNNING = 'running'

NOT_DELIVERIES = (NO_PARAMS, INVALID_PARAMS, RUNNING)

# Messages of the stage 'Validate parameters'. The build is judged by the rules it ran with: the digest
# became required only on 2026-11-22, so earlier releases without a digest were valid deliveries.
INVALID_PARAMETER_MESSAGES = ('GIT_COMMIT_FULL must be a full 40-character hash', 'must look like sha256:')

# Messages of the catch blocks of the production stage (Jenkinsfile.cd, .cd.ui, .cd.proxy).
CHECK_FAILED = ('Verification after the switch failed (', 'Verification failed (')
ROLLED_BACK_MESSAGES = ('Release rolled back to ', 'Release failed and was rolled back: ')
NOT_RESTORED = 'so the image was not restored'

COMPONENT_OF_JOB = {'shelter-cd': 'shelter-api', 'shelter-cd-ui': 'shelter-ui', 'shelter-cd-proxy': 'shelter-proxy'}


STAGE_ERRORS = set()


def http_open(url):
    request = urllib.request.Request(url)
    user, token = os.environ.get('JENKINS_USER'), os.environ.get('JENKINS_TOKEN')
    if user and token:
        credentials = base64.b64encode(f'{user}:{token}'.encode()).decode()
        request.add_header('Authorization', f'Basic {credentials}')
    return urllib.request.urlopen(request, timeout=30)


def http_get(url):
    with http_open(url) as response:
        return json.load(response)


def http_text(url):
    with http_open(url) as response:
        return response.read().decode('utf-8', errors='replace')


def rollback_outcome(log):
    """What the log of a build whose production stage failed says about production.

    ROLLED_BACK: a check failed after production was changed and the previous version serves again.
    ROLLBACK_FAILED: a check failed after the change and the previous version was not restored.
    PIPELINE_FAILED: no check failed after a change; the stage stopped before it changed production.
    """
    if not any(message in log for message in CHECK_FAILED):
        return PIPELINE_FAILED
    if NOT_RESTORED in log or not any(message in log for message in ROLLED_BACK_MESSAGES):
        return ROLLBACK_FAILED
    return ROLLED_BACK


def cached_log(base, job, number):
    """A function that reads the console log of one build once, on the first call."""
    cache = []

    def read():
        if not cache:
            cache.append(http_text(f'{base}/job/{job}/{number}/consoleText'))
        return cache[0]
    return read


def restore_minutes(base, job, number):
    """Time to restore recorded by the build (restore-event-*.json), or None when it has no record."""
    try:
        event = http_get(f'{base}/job/{job}/{number}/artifact/restore-event-{COMPONENT_OF_JOB[job]}.json')
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise
    detected = datetime.strptime(event['detectedAt'], '%Y-%m-%dT%H:%M:%SZ')
    restored = datetime.strptime(event['restoredAt'], '%Y-%m-%dT%H:%M:%SZ')
    return (restored - detected).total_seconds() / 60


def fetch_builds(base, job):
    tree = 'builds[number,result,timestamp,duration,building,actions[parameters[name,value]]]{0,1000}'
    return http_get(f'{base}/job/{job}/api/json?tree={tree}')['builds']


def fetch_stages(base, job, number):
    """Status and duration of the top level stages of one build.

    Reads the Pipeline Graph View endpoint (/stages/tree) and falls back to the older
    Pipeline Stage View endpoint (/wfapi/describe).
    """
    try:
        data = http_get(f'{base}/job/{job}/{number}/stages/tree')['data']['stages']
        return {s['name']: {'status': normalize_state(s['state']), 'durationMillis': s.get('totalDurationMillis')}
                for s in data}
    except urllib.error.HTTPError as first_error:
        try:
            stages = http_get(f'{base}/job/{job}/{number}/wfapi/describe')['stages']
        except urllib.error.HTTPError:
            STAGE_ERRORS.add(f'HTTP {first_error.code} {first_error.reason}')
            return {}
        return {s['name']: s for s in stages}


def normalize_state(state):
    state = str(state).upper()
    return {'FAILURE': 'FAILED', 'NOT_BUILT': 'NOT_EXECUTED', 'SKIPPED': 'NOT_EXECUTED'}.get(state, state)


def parameters(build):
    found = {}
    for action in build.get('actions', []):
        for p in action.get('parameters', []) or []:
            found[p['name']] = p.get('value')
    return found


def commit_time(commit):
    """Commit time in milliseconds, or None when the commit is not in the local clone."""
    if not commit:
        return None
    result = subprocess.run(['git', 'show', '-s', '--format=%ct', commit],
                            capture_output=True, text=True)
    if result.returncode != 0 or not result.stdout.strip():
        return None
    return int(result.stdout.strip()) * 1000


def lead_minutes(end_ms, committed_ms):
    # A commit that is newer than the build (a rewritten history) gives no valid lead time.
    if committed_ms is None or end_ms < committed_ms:
        return None
    return minutes(end_ms - committed_ms)


def stage_status(stages, name):
    stage = stages.get(name)
    return stage['status'] if stage else None


def is_test(params):
    return str(params.get('TEST_ROLLBACK')).lower() == 'true'


def classify(build, stages, read_log):
    """Kind of one build. read_log() returns the console log; it is called only when it is needed."""
    params = parameters(build)
    if build.get('building'):
        return RUNNING
    if not params.get('GIT_COMMIT_FULL'):
        return NO_PARAMS
    result = build.get('result')
    if not stages:
        # No stage data: decide from the result of the build alone.
        if result == 'SUCCESS':
            return RELEASED
        if result == 'ABORTED':
            return NOT_APPROVED
        return FAILED_UNKNOWN
    production = stage_status(stages, 'Production release')
    approval = stage_status(stages, 'Approval')
    if production in ('SUCCESS', 'UNSTABLE') and result in ('SUCCESS', 'UNSTABLE'):
        # UNSTABLE: the release is live, only its record could not be archived.
        return RELEASED
    if production in ('FAILED', 'UNSTABLE', 'ABORTED'):
        # The production stage started; only its log tells whether production was changed.
        outcome = rollback_outcome(read_log())
        if outcome == ROLLED_BACK and is_test(params):
            return TEST_ROLLBACK
        return outcome
    # The production stage did not run (NOT_EXECUTED or missing): production was not touched.
    if stage_status(stages, 'Staging') == 'FAILED' and any(m in read_log() for m in INVALID_PARAMETER_MESSAGES):
        return INVALID_PARAMS
    if approval in ('FAILED', 'ABORTED', 'NOT_EXECUTED') and stage_status(stages, 'Staging') == 'SUCCESS':
        return NOT_APPROVED
    return STOPPED_EARLY


def minutes(milliseconds):
    return milliseconds / 60000


def median(values):
    return statistics.median(values) if values else None


def show(value, digits=1):
    return '-' if value is None else f'{value:.{digits}f}'


def collect(base, component, job, since_ms):
    rows = []
    for build in fetch_builds(base, job):
        if build['timestamp'] < since_ms:
            continue
        stages = fetch_stages(base, job, build['number']) if not build.get('building') else {}
        kind = classify(build, stages, cached_log(base, job, build['number']))
        params = parameters(build)
        end = build['timestamp'] + build['duration']
        committed = commit_time(params.get('GIT_COMMIT_FULL'))
        approval = stages.get('Approval', {}).get('durationMillis')
        production = stages.get('Production release', {}).get('durationMillis')
        rows.append({
            'component': component,
            'job': job,
            'build': build['number'],
            'started': datetime.fromtimestamp(build['timestamp'] / 1000, timezone.utc).isoformat(timespec='seconds'),
            'kind': kind,
            'commit': (params.get('GIT_COMMIT_FULL') or '')[:7],
            'build_min': minutes(build['duration']),
            'approval_wait_min': minutes(approval) if approval is not None and kind in (RELEASED, ROLLED_BACK, TEST_ROLLBACK) else None,
            'production_min': minutes(production) if production is not None else None,
            'restore_min': restore_minutes(base, job, build['number']) if kind in (ROLLED_BACK, TEST_ROLLBACK) else None,
            'commit_to_end_min': lead_minutes(end, committed),
            'end_ms': end,
            'start_ms': build['timestamp'],
        })
    return rows


def first_releases(released):
    """The first release of every commit: the lead time counts a change once, not every redelivery."""
    first = {}
    for row in sorted(released, key=lambda r: r['end_ms']):
        first.setdefault(row['commit'], row)
    return list(first.values())


def summarize(component, rows):
    released = [r for r in rows if r['kind'] == RELEASED]
    failed = [r for r in rows if r['kind'] in (ROLLED_BACK, ROLLBACK_FAILED)]
    tests = [r for r in rows if r['kind'] == TEST_ROLLBACK]
    pipeline = [r for r in rows if r['kind'] == PIPELINE_FAILED]
    stopped = [r for r in rows if r['kind'] == STOPPED_EARLY]
    refused = [r for r in rows if r['kind'] == NOT_APPROVED]
    unknown = [r for r in rows if r['kind'] == FAILED_UNKNOWN]
    deliveries = [r for r in rows if r['kind'] not in NOT_DELIVERIES]
    reached = released + failed  # changes that reached production, deliberate rollbacks excluded

    print(f'### {component}\n')
    print('| Measure | Value |')
    print('| --- | --- |')
    print(f'| Delivery builds (without builds with missing or invalid parameters and running ones) | {len(deliveries)} |')
    if released:
        first = min(r['start_ms'] for r in released)
        last = max(r['end_ms'] for r in released)
        weeks = max((last - first) / (7 * 24 * 3600 * 1000), 1)
        print(f'| Production releases (deployment frequency) | {len(released)} in {weeks:.1f} weeks, {len(released) / weeks:.1f} per week |')
    else:
        print('| Production releases (deployment frequency) | 0 |')
    changes = [r for r in first_releases(released) if r['commit_to_end_min'] is not None]
    lead_total = [r['commit_to_end_min'] for r in changes]
    approval_wait = [r['approval_wait_min'] or 0 for r in changes]
    lead_without_wait = [r['commit_to_end_min'] - (r['approval_wait_min'] or 0) for r in changes]
    print(f'| Lead time, commit on main to the end of its first release, median (min) | {show(median(lead_total))} (n={len(lead_total)}) |')
    print(f'| ... of which waiting for the approval, median (min) | {show(median(approval_wait))} (n={len(approval_wait)}) |')
    print(f'| ... without the approval wait, median (min) | {show(median(lead_without_wait))} (n={len(lead_without_wait)}) |')
    if reached:
        print(f'| Change failure rate (failed after the change / reached production) | {len(failed)} / {len(reached)} = {100 * len(failed) / len(reached):.0f} % |')
    else:
        print('| Change failure rate | - |')
    if any(r['kind'] == ROLLBACK_FAILED for r in failed):
        print(f'| ... of which the previous version was not restored | {sum(r["kind"] == ROLLBACK_FAILED for r in failed)} |')
    print(f'| Production stage failed before changing production (pipeline failure) | {len(pipeline)} |')
    print(f'| Stopped before production (staging failed) | {len(stopped)} |')
    print(f'| Not approved (rejected, timed out or aborted) | {len(refused)} |')
    if unknown:
        print(f'| Failed, stage unknown (no stage data) | {len(unknown)} |')
    restore = [r['restore_min'] for r in failed if r['restore_min'] is not None]
    print(f'| Time to restore after a failed change, median (min) | {show(median(restore))} (n={len(restore)} of {len(failed)} with a record) |')
    test_restore = [r['restore_min'] for r in tests if r['restore_min'] is not None]
    print(f'| Time to restore in deliberate rollbacks (TEST_ROLLBACK), median (min) | {show(median(test_restore))} (n={len(test_restore)} of {len(tests)} with a record) |')
    print()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--since', default='1970-01-01', help='only builds from this date (YYYY-MM-DD)')
    parser.add_argument('--csv', help='also write one row per build to this file')
    parser.add_argument('--debug', action='store_true',
                        help='print the stages of the latest builds of every job and stop')
    args = parser.parse_args()

    base = os.environ.get('JENKINS_URL', 'https://shelter-jenkins.duckdns.org').rstrip('/')
    since_ms = int(datetime.strptime(args.since, '%Y-%m-%d').replace(tzinfo=timezone.utc).timestamp() * 1000)

    try:
        run(args, base, since_ms)
    except urllib.error.HTTPError as error:
        if error.code in (401, 403):
            sys.exit('Jenkins refused the request: set JENKINS_USER and JENKINS_TOKEN (an API token).')
        sys.exit(f'Jenkins answered {error.code} for {error.url}: {error.reason}')
    except urllib.error.URLError as error:
        sys.exit(f'Jenkins is not reachable at {base}: {error.reason}')


def run(args, base, since_ms):
    if args.debug:
        for job in JOBS.values():
            for build in fetch_builds(base, job)[:6]:
                print(f"{job} #{build['number']} result={build['result']} building={build['building']} "
                      f"duration_ms={build['duration']} params={parameters(build)}")
                for name, stage in fetch_stages(base, job, build['number']).items():
                    print(f"    stage '{name}' status={stage['status']} duration_ms={stage.get('durationMillis')}")
        if STAGE_ERRORS:
            print('Stage data could not be read:', ', '.join(sorted(STAGE_ERRORS)))
        return

    everything = []
    for component, job in JOBS.items():
        rows = collect(base, component, job, since_ms)
        everything.extend(rows)
        summarize(component, rows)

    if STAGE_ERRORS:
        print('Note: stage data could not be read (' + ', '.join(sorted(STAGE_ERRORS)) + '), '
              'so approval wait and restore time are missing and failures are not split by stage.')

    if args.csv:
        with open(args.csv, 'w', newline='', encoding='utf-8') as handle:
            fields = ['component', 'job', 'build', 'started', 'kind', 'commit', 'build_min',
                      'approval_wait_min', 'production_min', 'restore_min', 'commit_to_end_min']
            writer = csv.DictWriter(handle, fieldnames=fields, extrasaction='ignore')
            writer.writeheader()
            writer.writerows(everything)
        print(f'Per-build rows written to {args.csv}')


if __name__ == '__main__':
    main()

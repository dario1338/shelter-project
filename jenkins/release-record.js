#!/usr/bin/env node
// Release records of the delivery pipelines. A record is one small JSON file that a build archives
// (archiveArtifacts); a rollback copies it from the build it is asked to go back to (copyArtifacts).
// Used by jenkins/render.groovy. Kept as a script (not Groovy) so it can be run and tested outside Jenkins.
//
//   entry <commit> <digest> <deployedAt> <buildUrl> [action]   print one record (validated);
//                                                              action is release (default) or rollback
//   get   <file> <commit|digest>                               print one field of a record (validated)
'use strict';
const fs = require('fs');

const DIGEST = /^sha256:[0-9a-f]{64}$/;
const COMMIT = /^[0-9a-f]{40}$/;
const TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;
const ACTIONS = ['release', 'rollback'];

function fail(message) {
  console.error(`release-record: ${message}`);
  process.exit(1);
}

function validate(record) {
  if (!record || !COMMIT.test(record.commit || '')) fail('commit must be a full 40-character hash');
  if (!DIGEST.test(record.digest || '')) fail('digest must look like sha256:<64 hex characters>');
  if (!TIME.test(record.deployedAt || '')) fail('deployedAt must look like 2026-11-13T18:30:00Z');
  if (!ACTIONS.includes(record.action)) fail('action must be release or rollback');
  return record;
}

const [command, ...args] = process.argv.slice(2);

if (command === 'entry') {
  const [commit, digest, deployedAt, buildUrl, action = 'release'] = args;
  const record = validate({ commit, digest, deployedAt, action, buildUrl: buildUrl || '' });
  process.stdout.write(JSON.stringify(record, null, 2) + '\n');
} else if (command === 'get') {
  const [file, field] = args;
  if (!['commit', 'digest'].includes(field)) fail('field must be commit or digest');
  let record;
  try {
    record = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    fail(`cannot read ${file}: ${e.message}`);
  }
  process.stdout.write(validate(record)[field] + '\n');
} else {
  fail('usage: entry|get (see the comment at the top of the file)');
}

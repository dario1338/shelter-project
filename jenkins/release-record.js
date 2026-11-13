#!/usr/bin/env node
// Release records of the delivery pipelines: one JSON file per component on the `releases` branch.
// Used by jenkins/render.groovy. Kept as a script (not Groovy) so it can be run and tested outside Jenkins.
//
//   entry <commit> <digest> <deployedAt> <buildUrl>   print one release record (validated)
//   add   <file> <entryFile>                           append the record to the file, set previousDigest
//   find  <file> <commitPrefix>                        print the digest of the newest release of that commit
'use strict';
const fs = require('fs');

const DIGEST = /^sha256:[0-9a-f]{64}$/;
const COMMIT = /^[0-9a-f]{40}$/;
const PREFIX = /^[0-9a-f]{7,40}$/;
const TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;

function fail(message) {
  console.error(`release-record: ${message}`);
  process.exit(1);
}

function load(file) {
  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    fail(`cannot read ${file}: ${e.message}`);
  }
  if (!data || !Array.isArray(data.releases)) {
    fail(`${file} has no "releases" array`);
  }
  return data;
}

const [command, ...args] = process.argv.slice(2);

if (command === 'entry') {
  const [commit, digest, deployedAt, buildUrl] = args;
  if (!COMMIT.test(commit || '')) fail('commit must be a full 40-character hash');
  if (!DIGEST.test(digest || '')) fail('digest must look like sha256:<64 hex characters>');
  if (!TIME.test(deployedAt || '')) fail('deployedAt must look like 2026-11-13T18:30:00Z');
  process.stdout.write(JSON.stringify({ commit, digest, deployedAt, buildUrl: buildUrl || '' }, null, 2) + '\n');
} else if (command === 'add') {
  const [file, entryFile] = args;
  const data = load(file);
  const entry = JSON.parse(fs.readFileSync(entryFile, 'utf8'));
  if (!COMMIT.test(entry.commit) || !DIGEST.test(entry.digest) || !TIME.test(entry.deployedAt)) {
    fail('the entry is not valid');
  }
  const last = data.releases[data.releases.length - 1];
  if (last && last.digest === entry.digest) {
    console.log('The newest record already holds this digest, nothing to add.');
  } else {
    if (last) entry.previousDigest = last.digest;
    data.releases.push(entry);
    fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
  }
} else if (command === 'find') {
  const [file, prefix] = args;
  if (!PREFIX.test(prefix || '')) fail('commit prefix must be 7 to 40 lowercase hex characters');
  const data = load(file);
  for (let i = data.releases.length - 1; i >= 0; i--) {
    const release = data.releases[i];
    if (release.commit.startsWith(prefix) && DIGEST.test(release.digest)) {
      process.stdout.write(release.digest + '\n');
      break;
    }
  }
} else {
  fail('usage: entry|add|find (see the comment at the top of the file)');
}

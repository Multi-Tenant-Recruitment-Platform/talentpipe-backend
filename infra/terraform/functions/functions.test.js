// Tests for the CloudFront Functions in this directory.
//
//   node --test infra/terraform/functions/
//
// CloudFront Functions are plain scripts that define handler(event), without
// module exports, so each file is evaluated in a fresh context and its
// handler called with events shaped like CloudFront's.
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function load(file) {
    const context = {};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, file), 'utf8'), context);
    return context.handler;
}

function viewerRequest(uri, headers = {}, ip = '203.0.113.7') {
    return { viewer: { ip }, request: { method: 'GET', uri, querystring: {}, headers } };
}

const TENANT = '3f1c2a9e-0b7d-4c1e-9a55-2f0c8d6e4b11';
const CANDIDATE = '9b0e7d1c-5a2f-4e3b-8c6d-1a2b3c4d5e6f';

test('spa-router: routes of the app get index.html', () => {
    const handler = load('spa-router.js');
    for (const uri of ['/', '/dashboard', '/dashboard/jobs/3f1c2a9e', '/jobs/senior-engineer-acme-0a1b2c',
        '/accept-invite', '/verify-email']) {
        assert.equal(handler(viewerRequest(uri)).uri, '/index.html', uri);
    }
});

test('spa-router: files pass through', () => {
    const handler = load('spa-router.js');
    for (const uri of ['/index.html', '/assets/index-3f2a9c.js', '/assets/index-77aa01.css', '/favicon.svg']) {
        assert.equal(handler(viewerRequest(uri)).uri, uri, uri);
    }
});

test('media-router: logos, covers and photos are served by their key', () => {
    const handler = load('media-router.js');
    for (const key of [`/tenant/${TENANT}/logo/a1b2.png`, `/tenant/${TENANT}/cover/c3d4.jpg`,
        `/candidate/${CANDIDATE}/photos/e5f6.webp`]) {
        assert.equal(handler(viewerRequest('/media' + key)).uri, key, key);
    }
});

test('media-router: CVs and anything else are not found', () => {
    const handler = load('media-router.js');
    for (const uri of [
        `/media/candidate/${CANDIDATE}/cvs/cv.pdf`,
        `/media/candidate/${CANDIDATE}/photos/../cvs/cv.pdf`,
        `/media/candidate/${CANDIDATE}/photos/`,
        `/media/tenant/${TENANT}/logo/nested/a.png`,
        `/media/tenant/${TENANT}/other/a.png`,
        `/media/tenant/not-a-uuid/logo/a.png`,
        '/media/',
        '/media',
    ]) {
        assert.equal(handler(viewerRequest(uri)).statusCode, 404, uri);
    }
});

test('viewer-ip: the backend is told the viewer address', () => {
    const handler = load('viewer-ip.js');
    const request = handler(viewerRequest('/api/v1/auth/login', {}, '198.51.100.23'));
    assert.equal(request.headers['x-viewer-ip'].value, '198.51.100.23');
});

test('viewer-ip: a value sent by the viewer is replaced', () => {
    const handler = load('viewer-ip.js');
    const forged = { 'x-viewer-ip': { value: '10.0.0.1' } };
    const request = handler(viewerRequest('/api/v1/auth/login', forged, '198.51.100.23'));
    assert.equal(request.headers['x-viewer-ip'].value, '198.51.100.23');
});

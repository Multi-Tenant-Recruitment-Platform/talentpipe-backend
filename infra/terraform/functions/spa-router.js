// CloudFront Function, viewer request, default behaviour (the SPA bucket).
//
// A path whose last segment has no file extension is a route of the
// single-page app (/dashboard/jobs/..., /accept-invite?token=...), not a
// file: serve index.html and let the app's router take it. Files such as
// /assets/index-3f2a9c.js or /favicon.svg pass through unchanged.
//
// This is done here, not with CloudFront custom error pages: those apply to
// every origin, and would turn the API's real 403 and 404 answers into
// index.html with a 200.
function handler(event) {
    var request = event.request;
    var uri = request.uri;
    var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);

    if (lastSegment.indexOf('.') === -1) {
        request.uri = '/index.html';
    }
    return request;
}

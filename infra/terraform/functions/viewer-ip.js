// CloudFront Function, viewer request, /api/* (the backend).
//
// Tells the backend who the viewer is, in a header the viewer cannot set:
// whatever arrived under this name is replaced. The backend takes the client
// address from it (CLIENT_IP_HEADER) for the login rate limit.
//
// X-Forwarded-For cannot serve: its first entries are written by the caller,
// and its last is a CloudFront address that many viewers share.
function handler(event) {
    var request = event.request;
    request.headers['x-viewer-ip'] = { value: event.viewer.ip };
    return request;
}

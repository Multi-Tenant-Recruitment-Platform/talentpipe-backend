// CloudFront Function, viewer request, /media/* (the media bucket).
//
// The backend links to stored images as <site>/media/<object key>
// (AWS_CDN_BASE_URL), so the key is the path without "/media". Only logos,
// cover images and profile photos are public. Anything else, CVs above all,
// gets a 404 here without S3 being asked; the bucket policy refuses them too.
// CVs reach people only through the API, which checks who is asking.
var PUBLIC_KEY = /^\/(tenant\/[0-9a-fA-F-]{36}\/(logo|cover)|candidate\/[0-9a-fA-F-]{36}\/photos)\/[^\/]+$/;

function handler(event) {
    var request = event.request;
    var key = request.uri.substring('/media'.length);

    if (!PUBLIC_KEY.test(key)) {
        return {
            statusCode: 404,
            statusDescription: 'Not Found'
        };
    }
    request.uri = key;
    return request;
}

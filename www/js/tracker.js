function convertTime(d, on) {
    function seconds(d) {
        var res = "" + Math.floor((d / 1000) % 60);
        if (res.length === 1)
            res = '0' + res;
        return res;
    }

    function minutes(d) {
        var res = "" + Math.floor((d / 60000) % 60);
        if (res.length === 1)
            res = '0' + res;
        return res;
    }

    function hours(d) {
        var res = "" + Math.floor(d / (60000 * 60));
        return res;
    }
    on.hours = hours(d)
    on.minutes = minutes(d)
    on.seconds = seconds(d)
    return on;
}

var app = angular.module('videos', ['ui'])
app.filter('todate', function() {
    return function(num) {
        if (typeof num === 'number')
            return new Date(num);
        return Date.parse(num)
    }
});

app.filter('duration', function() {
    return function(num) {
        if (typeof num === 'string') {
            num = parseInt(num)
        }
        var x = {};
        convertTime(num, x);
        var result = [];
        if (x.hours > 0) {
            result.push(x.hours + ' hours');
        }
        var m = parseInt(x.minutes) + (x.seconds > 0 ? 1 : 0);
        result.push(m + " min");
        //result.push(x.seconds + "s");
        return result.join(' ');
    }
});

app.filter('size', function() {
    return function(bytes) {
        if (typeof bytes === 'string') {
            try {
                bytes = parseInt(bytes);
            } catch (err) {
                console.log(err)
                bytes = 0;
            }
        }
        var k = Math.ceil(bytes / 1024);
        var rem = Math.floor(bytes % 1024);
        if (k > 1024) {
            var mb = Math.ceil((bytes / 1024) / 1024);
            return mb + " megabytes";
        } else if (bytes > 1024) {
            return k + " kilobytes";
        } else {
            return bytes + "bytes";
        }
    }
})

function Throttle(max) {
    var self = this;
    var throttled = [];
    var running = 0;

    var onTimeout = function() {
        self.handle = null;
        if (running >= max) {
            initTimeout();
        } else {
            runSome();
        }
    }

    function initTimeout() {
        if (!self.handle) {
            self.handle = setTimeout(onTimeout, 1000);
        }
    }

    function runSome() {
        while (running < max && throttled.length > 0) {
            runOne(throttled.pop())
        }
        if (throttled.length > 0) {
            initTimeout();
        }
    }

    function runOne(what) {
        running++;
        what(self.done);
    }

    this.done = function() {
        running--;
        runSome();
    }

    this.throttle = function(what) {
        if (running >= max) {
            throttled.push(what);
            initTimeout();
        } else {
            runOne(what);
        }
    }
}

function ServerLog($scope, $http) {
    // the last received msg
    $scope.log = [];
    $scope.status = {
        metadataScans: {
            running: 0,
            enqueued: 0
        },
        hashing: {
            running: 0,
            enqueued: 0
        },
        thumbnailGeneration: {
            running: 0,
            enqueued: 0
        }
    }

// handles the callback from the received event
    var handleCallback = function(msg) {
        var message = JSON.parse(msg.data);

        $scope.$apply(function() {
            switch (message.data.type) {
                case 'stats' :
                    $scope.log.push(message.data);
                    break;
                default :
                    $scope.status = message.data;
            }
        });
    }

    var source = new EventSource('/api/stats');
    source.addEventListener('message', handleCallback, false);

    source.addEventListener('open', function(e) {
        console.log("Cnnection opened");
    }, false);
    source.addEventListener('error', function(e) {
        if (e.readyState == EventSource.CLOSED) {
            // Connection was closed.
            console.log("Evt source closed");
        }
    }, false);
}

function Videos($scope, $http) {
    var fields = ['_id', 'hashes.sha1', 'fileName', 'title', 'description',
        'metadata.durationMillis', 'metadata.durationraw', 'stat.size', 'lastModified',
        'metadata.video.resolution.w', 'metadata.video.resolution.h', 'metadata.video.codec',
        'metadata.video.container'
    ];

    function Query() {
        this.codec = '';
        this.format = '';
        this.minutes = -1;
        this.hours = -1;
        this.durationType = '>=';
        this.includeDuration = false;
        this.includeCodec = false;
        this.includeFormat = false;
    }

    function queryToString(self) {
        var result = [];
        if (self.includeDuration && (self.hours >= 0 || self.minutes >= 0)) {
            var dur = 0;
            if (self.hours > 0) {
                dur += (self.hours * 60 * 60 * 1000);
            }
            if (self.minutes > 0) {
                dur += (60 * 1000 * self.minutes)
            }
            if (dur > 0) {
                result.push('metadata.durationMillis=' + self.durationType + dur);
            }
        }
        if (self.includeFormat && self.format !== '') {
            result.push('metadata.video.container=' + self.format)
        }
        if (self.includeCodec && self.codec !== '') {
            result.push('metadata.video.codec=' + self.codec);
        }
        if (result.length > 0) {
            return '?' + result.join('&');
        }
        return '';
    }

    $scope.thumbnailsForId = {};

    $scope.query = {
        codec: '',
        format: '',
        minutes: -1,
        hours: -1,
        durationType: '>=',
        includeDuration: false,
        includeCodec: false,
        includeFormat: false,
        searchText: 'homeland'
    }

    var loadTimeout = null;
    function enqueue() {
        $scope.loading = true;
        $scope.problem = null;
        if (loadTimeout) {
            clearTimeout(loadTimeout);
            loadTimeout = null;
        }
        loadTimeout = setTimeout(load, 1500);
    }

    function load() {
        var q = queryToString($scope.query);
        if (q.length === 0) {
            q = '?';
        } else {
            q += '&';
        }
        q += 'fields=' + fields.join(',');

        var path = '/api/videos';
        if ($scope.query.searchText !== '') {
            path = '/api/search';
            if (q.length === 0) {
                q = '?'
            } else {
                q += '&'
            }
            q += 'search=' + $scope.query.searchText;
        }

        var url = path + q;
        $http.get(url).success(function(videos) {
            $scope.loading = false;
            $scope.problem = null;
            processVideos(videos);
        }).error(function(err) {
            $scope.problem = err;
            $scope.loading = false;
        });
    }
    $scope.enqueue = enqueue;

    $scope.$watch('query.durationType + query.codec + query.minutes + query.hours + query.format + query.includeDuration + query.includeCodec + query.includeFormat + query.searchText', enqueue);

    $scope.deleteVideo = function(video) {
        $scope.loading = true;
        $http.delete('/api/videos?hashes.sha1=' + video.hashes.sha1).success(function() {
            console.log("DELETED ", video);
            $scope.loading = false;
            $scope.problem = null;
        }).error(function(err) {
            $scope.problem = err;
            $scope.loading = false;
        });
        var nue = [];
        for (var i = 0; i < $scope.videos.length; i++) {
            var vid = $scope.videos[i];
            if (vid._id !== video._id) {
                nue.push(vid);
            }
        }
        $scope.videos = nue;
    }

    $scope.selectThumbnail = function(tnl) {
        $scope.selectedThumbnail = tnl;
    }

    var throttle = new Throttle(3);

    function processVideos(videos) {
        $scope.videos = videos;
        $scope.videos.forEach(function(video) {
            if (video) {
                if (video.metadata.title) {
                    video.displayName = video.metadata.title;
                } else {
                    video.displayName = video.fileName;
                }
                if (video.displayName) {
                    video.displayName = video.displayName.replace(/_/g, ' ').replace(/-/g, ' ').replace(/\./g, ' ').toLowerCase();
                }
            }
            if (!$scope.thumbnailsForId[video._id]) {
                throttle.throttle(function(onDone) {
                    $http.get('/api/thumbnails?hashes.sha1=' + video.hashes.sha1).success(function(thumbnails) {
                        $scope.thumbnailsForId[video._id] = thumbnails;
                        video.thumbnails = thumbnails;
                        onDone();
                    }).error(function() {
                        onDone();
                    });
                })
            } else {
                video.thumbnails = $scope.thumbnailsForId[video._id];
            }
        });
        $scope.videos.sort(function(a, b) {
            return a.displayName < b.displayName ? -1 : a.displayName > b.displayName ? 1 : 0;
        });
    }

    enqueue();

    $http.get('/api/facets/metadata.video.container').success(function(facets) {
        $scope.formats = facets;
    });

    $http.get('/api/facets/metadata.video.codec').success(function(facets) {
        $scope.codecs = facets;
    });
}

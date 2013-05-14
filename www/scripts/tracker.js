function convertTime(d, on) {
    on = on || {};
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
            result.push(x.hours + 'h');
        }
        var m = parseInt(x.minutes) + (x.seconds > 0 ? 1 : 0);
        result.push(m + "m");
        //result.push(x.seconds + "s");
        return result.join(' ');
    }
})

function Times($scope, $http, lookingAt, $location, urls) {
    var u = decodeURIComponent(document.URL);

    console.log('LOOKINGAT PATH + "' + lookingAt.name + '"')
    console.log('CATEGORY: ' + $scope.category)

    $http.get(urls.userPath(lookingAt.name,'list')).success(function(categories) {
        $scope.categories = categories;
    })
    
    $scope.categoryClass = function(cat) {
        return $scope.category === cat ? "active" : "inactive";
    }
    
    lookingAt.get().success(function(lu){
        $scope.lookingAtUserName = lu.displayName;
    });


    var findStart = /.*\?.*start=\D{0,2}(\d+)/;
    var findEnd = /.*\?.*end=\D{0,2}(\d+)/;
    var urlEnd = new Date();
    var urlStart = new Date(urlEnd.getTime());
    urlStart.setMonth(urlStart.getMonth() - 1);

    if (findStart.test(u)) {
        var st = findStart.exec(u)[1];
        urlStart = new Date(parseInt(st))
        console.log('Matched start: ' + urlStart + " for " + st);
    }

    if (findEnd.test(u)) {
        var et = findEnd.exec(u)[1]
        urlEnd = new Date(parseInt(et))
        console.log('Matched end: ' + urlEnd + " for " + et);
    }
    $scope.category = window.location.hash.substring(1) || 'work';

    function load(cat) {
        cat = cat || 'work';
        $scope.category = cat;
//        $location.hash(cat);
//        window.location.hash=cat;
        var fetchFrom = urls.userPath(lookingAt.name, 'time/' + $scope.category);
        if (/.*?\?(.*?)/.test(u + "")) {
            fetchFrom += '?' + /.*?\?(.*?)$/.exec(u + "")[1];
        }
        fetchBase = urls.userPath(lookingAt.name, 'time/' + $scope.category);
        $http.get(fetchFrom).success(function(items) {
            var total = 0;
            var aggregate = {};

            $scope.showHide = true;

            $scope.start = urlStart;
            $scope.end = urlEnd;
            $scope.dateOptions = {format: 'dd/mm/yyyy'}

            $scope.navRange = function(start, end) {
                var endOfDay = new Date(end.getTime());
                endOfDay.setHours(23);
                endOfDay.setMinutes(59);
                endOfDay.setSeconds(59);
                var rex = /(.*?)\?.*?/;
                var base = rex.test(document.URL) ? rex.exec(document.URL)[1] : document.URL;

                window.location = base + '?start=>=' + start.getTime() + '&end=<=' + endOfDay.getTime();
            }

            var byDate = {}

            $scope.byDate = byDate;

            $scope.byDay = {};

            items.forEach(function(item) {
                var d = item.dur;
                total += d;
                convertTime(d, item)
                item.date = new Date(item.start);
                item.endDate = new Date(item.end);

                // Round to nearest midnight
                var day = new Date(item.start);
                day.setHours(0);
                day.setMinutes(0);
                day.setSeconds(0);

                var byType = byDate[day];

                var dd = $scope.byDay[day];
                if (!dd) {
                    $scope.byDay[day] = 0;
                    dd = $scope.byDay[day];
                }

                if (!byType) {
                    byDate[day] = {};
                    byType = byDate[day]
                }

                var individ = byType[item.activity];
                if (!individ) {
                    byType[item.activity] = individ = [];
                    individ.total = 0;
                }

                individ.push(item);
                individ.total += item.dur;
                $scope.byDay[day] += item.dur;
                convertTime(individ.total, individ); //XXX inefficient

                if (item.running) {
                    item.style = 'color: #44BB44';
                } else {
                    item.style = '';
                }
                var ag = aggregate[item.activity];
                if (ag) {
                    ag.total += d;
                } else {
                    ag = d;
                    aggregate[item.activity] = {total: ag};
                }

            });

            var sortit = [];
            for (var key in byDate) {
                sortit.push(key);
            }
            sortit.sort(function(a, b) {
                var da = Date.parse(a)
                var db = Date.parse(b)
                return da < db ? -1 : da > db ? 1 : 0;
            });
            $scope.dates = sortit;

            $scope.items = items;
            $scope.total = convertTime(total, {});

            var aggs = [];
            for (var key in aggregate) {
                var d = aggregate[key].total;
                convertTime(d, aggregate[key]);
                aggregate[key].activity = key;
                aggs.push(aggregate[key]);
            }
            $scope.aggregate = aggs;
        });
    }
    load();
    $scope.load = load;
}

var app = angular.module('surveys', ['ngCookies', 'ui']);

var USER_DISPLAY_NAME_COOKIE = "dn";
var USER_COOKIE_NAME = "ac";
var API_BASE = "/time/"

app.service('status', function($rootScope) {
    function setProblem(err) {
        $rootScope.success = null;
        $rootScope.problem = err;
    }

    function setSuccess(succ) {
        $rootScope.problem = null;
        $rootScope.success = succ;
    }

    function clear() {
        $rootScope.problem = null;
        $rootScope.success = null;
    }
    this.clear = clear;

    this.__defineGetter__('errorHandler', function() {
        clear();
        $rootScope.loading = true;
        return function(err) {
            $rootScope.loading = false;
            setProblem(err);
        }
    });

    this.__defineGetter__('successHandler', function() {
        clear();
        $rootScope.loading = true;
        return function(succ) {
            setSuccess(succ);
            $rootScope.loading = false;
        }
    });

    this.__defineSetter__('problem', function(err) {
        setProblem(err)
    });

    this.__defineSetter__('success', function(succ) {
        setSuccess(succ)
    })

    this.successHandler = function(success) {
        setSuccess(success)
    }
});

app.service('user', function($cookies, $http, $rootScope) {
    var un = window.location.pathname.split('/')[2];
    var self = this;

    // This *is* a potential race condition
    if ($cookies[USER_COOKIE_NAME]) {
        un = /"?(.*?):.*/.exec($cookies[USER_COOKIE_NAME])[1];
        if (un) {
            self.name = un;
            console.log('GOT NAME FROM COOKIE: ' + un)
        } else {
            console.log('NO USER FROM COOKIE')
            self.get().success(function(user) {
                console.log('LOADED ' + user.name)
                un = user.name;
            });
        }
    }
    this.name = un;

    this.path = API_BASE + 'users/' + un;

    this.__defineGetter__('displayName', function() {
        if (self.dn) {
            return self.dn;
        }
        var dn = $cookies[USER_DISPLAY_NAME_COOKIE];
        if (dn && /"/.test(dn)) {
            dn = dn.replace(/"/g, '');
        }
        return self.dn = dn;
    });

    this.setDisplayName = function(nm) {
        var old = self.name;
        self.name = nm;
        return $http.post(self.path + '?displayName=' + nm, '').error(function(err) {
            self.name = old;
        }).success(function(records) {
            $rootScope.$broadcast('userDisplayNameChanged', nm);
        });
    }

    this.setPassword = function(password) {
        return $http.post(self.path + '/password', password);
    }

    this.get = function() {
        return $http.get(API_BASE + "whoami");
    }
});

app.service('lookingAt', function(user, $http, $rootScope) {
    var un = window.location.pathname.split('/')[2];
    var self = this;
    this.name = un;
    $rootScope.lookingAtUserName = un;
    self.path = API_BASE + 'users/' + un;
    console.log('LOOKING AT ' + un + "'")
    if (user.name === un) {
        this.get = user.get;
    } else {
        this.get = function() {
            return $http.get(API_BASE + "whoami?user=" + un).success(function(u) {
                $rootScope.lookingAtUserName = u.name;
                $rootScope.lookingAtUser = u;
                self.path = API_BASE + '/users/' + u.name;
            });
        }
    }
});

app.factory('loadingInterceptor', function($q, $rootScope) {
    return function(promise) {
        $rootScope.loading = true;
        return promise.then(function(response) {
            $rootScope.loading = false;
            return response;
        }, function(response) {
            $rootScope.loading = false;
            // do something on error
            return $q.reject(response);
        });
    };
});

app.config(function($httpProvider) {
    $httpProvider.responseInterceptors.push('loadingInterceptor');
});

app.directive('autoComplete', function($timeout) {
    return function($scope, iElement, iAttrs) {
        console.log('AC ', iElement, iAttrs)
        iElement.autocomplete({
            source: $scope.$eval(iAttrs.uiItems),
            select: function() {
                $timeout(function() {
                    iElement.trigger('input');
                }, 0);
            }
        });
    };
});

app.directive('timepicker', function($timeout, dateFilter) {
    var result = {
        compile: function compile(tElement, tAttrs, transclude) {
            return {
                post: function postLink(scope, iElement, iAttrs, controller) {
                    var tp = iElement.timepicker();
                    if (iAttrs['minuteStep']) {
                        tp.minuteStep = iAttrs['minuteStep'];
                    }
                    iElement.bind('mousewheel', function(evt) {
                        if (delta === 0) {
                            return;
                        }
                        var data = tp.data();
                        var delta = evt.originalEvent.wheelDelta;
                        console.log('MW!! ' + delta + ':', evt)
                        var direction = delta > 0 ? 1 : -1;
                        var step = iAttrs['minuteStep'] ? parseInt(iAttrs['minuteStep']) : 5;
                        console.log('STEP ' + step + " dir " + direction)
                        if (direction === 1) {
                            tp.timepicker('decrementMinute', step);
                            scope.$apply();
                        } else {
                            tp.timepicker('incrementMinute', step);
                            scope.$apply();
                        }
                    });
                    iElement.timepicker().on('changeTime.timepicker', function(e) {
                        scope[iAttrs.ngModel] = e.time;
                        if (!scope.$$phase)
                            scope.$apply();
                    });
                }
            }
        }
    }
    return result;
});

app.filter('two', function() {
    return function(num) {
        if (typeof num === 'string') {
            num = parseInt(num)
        }
        var result = '' + num;
        if (result.length === 1) {
            result = '0' + result;
        }
        return result;
    }
})

function Status($scope, status) {
    $scope.clear = status.clear;
}

function User($scope, user, $rootScope, lookingAt, $http) {
    console.log('USER ' + user.name);
    console.log('LOOKING AT ' + lookingAt.name)
    console.log('DN ' + user.displayName)
    if (!user.displayName) {
        user.get().success(function(u) {
            $scope.userDisplayName = u.displayName;
        });
    }
    $scope.userDisplayName = user.displayName;
    $scope.userName = user.name;
    $scope.lookingAtUserName = lookingAt.name;
    $rootScope.$on('userDisplayNameChanged', function(evt, name) {
        $scope.userDisplayName = name;
    });

    $scope.logout = function() {
        $http.get(API_BASE + 'testLogin?logout=true').success(function() {
            $rootScope.success = 'Logged out';
            window.location = '/';
        })
    }
}

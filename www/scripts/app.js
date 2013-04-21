var app = angular.module('surveys', ['ngCookies', 'ui']);

var USER_DISPLAY_NAME_COOKIE = "dn";
var USER_COOKIE_NAME = "ac";

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
    this.name = un;

    this.path = '/time/users/' + un;

    this.__defineGetter__('displayName', function() {
        if (self.dn) {
            return self.dn;
        }
        var dn = $cookies['dn'];
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
        return $http.get("/time/whoami");
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

function Status($scope, status) {
    $scope.clear = status.clear;
}

function User($scope, user, $rootScope) {
    $scope.userDisplayName = user.displayName;
    $rootScope.$on('userDisplayNameChanged', function(evt, name) {
        $scope.userDisplayName = name;
    });
}

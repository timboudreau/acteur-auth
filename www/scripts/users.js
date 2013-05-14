(function() {
    'use strict';

    angular.module('users', ['urls', 'ngCookies', 'status'])
            .service('user', function(urls, $cookies, $http, status, $rootScope) {
        var self = this;
        
        this.getPath = function(what) {
            return urls.userPath(self.name, what)
        }

        function watchCookies(names, callback) {
            var orig = {};
            for (var i = 0; i < names.length; i++) {
                orig[names[i]] = $cookies[names[i]]
            }
            var ival = setInterval(function() {
                for (var i = 0; i < names.length; i++) {
                    if ($cookies[names[i]] !== orig[names[i]]) {
                        callback(names[i], $cookies[names[i]]);
                        clearInterval(ival);
                        break;
                    }
                }
            }, 1000);
        }

        this.onLoggedInUserChange = function(callback) {
            watchCookies(urls.cookieNames, function() {
                callback(self.name)
            });
        }

        this.__defineGetter__("name", function() {
            var cookieNames = urls.cookieNames;
            for (var i = 0; i < cookieNames.length; i++) {
                var ck = $cookies[cookieNames[i]];
                if (ck) {
                    ck = ck.replace(/"/g, '');
                    if (ck) {
                        console.log('found ' + cookieNames[i] + ' - ' + ck)
                        var res = /.*?:(.*)['"]?/.exec(ck);
                        if (res) {
                            var result = res[1].replace(/"/g, '').replace(/'/g, '');
                            return result;
                        }
                    }
                }
            }
        });
        
        this.__defineGetter__("info", function() {
            // Return an object rather than a string, for ease of watching
            // in controllers
            return {
                name : self.name,
                displayName : self.displayName
            }
        });

        this.__defineGetter__("names", function() {
            var cookieNames = urls.cookieNames;
            var result = [];
            for (var i = 0; i < cookieNames.length; i++) {
                var ck = $cookies[cookieNames[i]];
                if (ck) {
                    ck = ck.replace(/"/g, '');
                    if (ck) {
                        console.log('found ' + cookieNames[i] + ' - ' + ck)
                        var res = /.*?:(.*)['"]?/.exec(ck);
                        if (res) {
                            var nm = res[1].replace(/"/g, '').replace(/'/g, '');
                            result.push(nm);
                        }
                    }
                }
            }
            return result;
        });

        this.__defineGetter__("displayName", function() {
            if (self._dn) {
                return self._dn;
            }
            var res = $cookies[urls.displayNameCookie];
            if (res) {
                return res.replace(/"/g, '');
            }
        });

        this.__defineGetter__("path", function() {
            return urls.userPath(self.name);
        });

        this.setDisplayName = function(nm) {
            var old = self.displayName;
            self._dn = nm;
            return $http.post(self.path + '?displayName=' + nm, '').success(function(records) {
                $rootScope.$broadcast('userDisplayNameChanged', nm);
            }).error(status.errorHandler).error(function() {
                self._dn = old;
            });
        }

        this.__defineGetter__('home', function() {
            return self.path;
        })

        this.setPassword = function(password) {
            return $http.post(self.path + '/password', password).error(status.errorHandler);
        }

        this.get = function() {
            return $http.get(urls.path("whoami")).error(status.errorHandler);
        }

        this.signup = function(username, password, displayName) {
            return $http.post(urls.userPath(username, 'signup?displayName=' + displayName)).error(status.errorHandler);
        }

        this.login = function(username, password) {
            var credentials = Base64.encode(username + ':' + password);

            var opts = {
                withCredentials: true,
                method: 'POST',
//            url: '/time/users/'+ $scope.username + '/testLogin?auth=true',
                url: urls.userPath(username, 'testLogin?auth=true'),
                data: '',
                headers: {
                    Authorization: 'Basic ' + credentials
                }
            }

            $http.defaults.headers['Authorization'] = 'Basic ' + credentials;
//            $http(opts).success(function(info) {
//                console.log("GOT BACK", info);
//
//                if (!info.credentialsPresent) {
//                    $scope.problem = 'Your browser did not send the credentials';
//                } else if (!info.valid) {
//                    $scope.problem = 'Invalid user name or password';
//                } else {
//                    var loc = '/users/' + $scope.username + "/";
//                    window.location = loc;
//                }
//            }).error(function(err) {
//                console.log("GOT BACK ", err);
//                $scope.problem = err;
//            })

        }
    }).service('lookingAt', function(urls, status, $http) {
        this.__defineGetter__("name", function() {
            return urls.currentUser;
        });
        this.get = function() {
            return $http.get(urls.path("whoami?user=" + urls.currentUser)).error(status.errorHandler);
        }
    });
})();

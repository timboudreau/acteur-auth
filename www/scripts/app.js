var app = angular.module('surveys', ['ngCookies', 'ui', 'http-auth-interceptor', 'urls', 'users', 'status', 'ui.bootstrap.dialog']);

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
    $httpProvider.defaults.headers.common['X-No-Authenticate'] = 'true';
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
                        var direction = delta > 0 ? 1 : -1;
                        var step = iAttrs['minuteStep'] ? parseInt(iAttrs['minuteStep']) : 5;
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

app.directive('loginDialog', function($dialog) {
    return {
        restrict: 'C',
        link: function(scope, elem, attrs) {

            var dlg = $dialog.dialog({dialogFade: true, keyboard: false, backdropClick: false});

            scope.$on('event:auth-loginRequired', function() {
                dlg.open('/partials/loginform.html', function(dialog) {
                    console.log('DIALOG INIT:')
                    scope.loginDialog = dialog;
                });
            });
            scope.$on('event:auth-loginConfirmed', function() {
                if (scope.loginDialog) {
                    scope.loginDialog.close();
                    scope.loginDialog = null;
                }
            });
        }
    }
});

app.controller({
    AuthController: function($scope, $http, $cookies, authService, urls, user) {
        $http.get(urls.path('auths')).success(function(auths) {
            $scope.auths = auths;
        });

        $scope.authPopup = function(auth) {
            var url = auth.loginPagePath;
//            signinWin = window.open(url + "?redir=/login.html");
            var pos = {x: '50%', y: '50%'}
            signinWin = window.open(url + "?redir=/login.html", "SignIn", "width=500,height=300,toolbar=0,scrollbars=0,status=0,resizable=0,location=0,menuBar=0,left=" + pos.x + ",top=" + pos.y + ",unadorned=true");

            var oldc = signinWin.close;
            signinWin.close = function() {
                console.log('CLOSE IT', new Error("Close"))
                oldc.apply(signinWin, arguments);
            }

            user.onLoggedInUserChange(function(userName) {
                if (userName) {
                    authService.loginConfirmed();
                    signinWin.close();
                }
            });

            if (signinWin) {
                signinWin.focus();
            }
        }
    },
    User: function($scope, user, $http, urls) {
        $scope.user = user.info;
        $scope.logout = function() {
            $http.post(urls.path('testLogin?logout=true'), '').success(function(){
                window.location = '/';
            });
        }
    },
    Status : function($scope, status) {
        
    },
    LoginController: function($scope, $http, authService, urls, status) {
        $scope.signUp = function() {
            if ($scope.password2 !== $scope.password) {
                status.problem = "Passwords do not match"
                return;
            }
            $scope.problem = null;
            var up = urls.userPath($scope.username, 'signup?displayName=' + $scope.displayName);
            $http.post(up, $scope.password).success(function(user) {
                $scope.user = user;
                $http.defaults.headers.common['X-No-Authenticate'] = 'true';
                $http.defaults.headers.common['Authorization'] = 'Basic ' + Base64.encode($scope.username + ':' + $scope.password);

                $http.get(urls.path('whoami')).success(function() {
                    window.location = urls.userHome($scope.username);
                });
            }).error(function(err) {
                $scope.problem = err;
            });
        }

        $scope.login = function() {
            if (!$scope.username || !$scope.password) {
                status.problem = "Username or password missing";
                return;
            }

            var credentials = Base64.encode($scope.username + ':' + $scope.password);

            var up = urls.path('testLogin?auth=true');
            console.log('USER PATH: ' + up)
            var opts = {
                withCredentials: true,
                method: 'POST',
                url: up,
                data: '',
                headers: {
                    Authorization: 'Basic ' + credentials
                }
            }

            $http.defaults.headers['Authorization'] = 'Basic ' + credentials;
            $http(opts).success(function(info) {
                console.log("GOT BACK", info);

                if (!info.success) {
                    $scope.problem = 'Invalid user name or password';
                } else {
                    authService.loginConfirmed();
                }
            }).error(function(err) {
                $scope.problem = err;
            })
        }
    }
})

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

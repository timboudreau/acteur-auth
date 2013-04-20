function getHTTPObject() {
    if (typeof XMLHttpRequest != 'undefined') {
        return new XMLHttpRequest();
    }
    try {
        return new ActiveXObject("Msxml2.XMLHTTP");
    } catch (e) {
        try {
            return new ActiveXObject("Microsoft.XMLHTTP");
        } catch (e) {
        }
    }
    return false;
}

function Login($scope, $http) {
    $scope.problem = false;

    var htt = getHTTPObject();

    $scope.signUp = function() {
        if ($scope.password2 !== $scope.password) {
            $scope.problem = "Passwords do not match"
            return;
        }
        $scope.problem = null;

        $http.post('/time/users/' + $scope.username + '/signup?displayName=' + $scope.displayName, $scope.password).success(function(user) {
            $scope.user = user;
            console.log("SIGN UP", user);
            $http.defaults.headers.common['Authorization'] = 'Basic ' + Base64.encode($scope.username + ':' + $scope.password);
            $http.get('/time/whoami').success(function() {
                var loc = '/users/' + $scope.username + "/";
                window.location = loc;
            });
        }).error(function(err) {
            $scope.problem = err;
        });
    };
    
    $scope.blindLogin = function() {
        $http.get('/time/testLogin?auth=false').success(function(result){
            console.log("BLIND LOGIN", result);
            if (result.valid && result.user) {
                var loc = '/users/' + $scope.username + "/";
                window.location = loc;
            }
        });
    }

    $scope.login = function() {
        if (!$scope.username || !$scope.password) {
            $scope.problem = "Username or password missing";
            return;
        }

        var credentials = Base64.encode($scope.username + ':' + $scope.password);

        var opts = {
            withCredentials: true,
            method: 'POST',
            url: '/time/users/'+ $scope.username + '/testLogin?auth=true',
            data: '',
            headers: {
                Authorization: 'Basic ' + credentials
            }
        }

        $http.defaults.headers['Authorization'] = 'Basic ' + credentials;
        $http(opts).success(function(info) {
            console.log("GOT BACK", info);

            if (!info.credentialsPresent) {
                $scope.problem = 'Your browser did not send the credentials';
            } else if (!info.valid) {
                $scope.problem = 'Invalid user name or password';
            } else {
                var loc = '/users/' + $scope.username + "/";
                window.location = loc;
            }
        }).error(function(err) {
            console.log("GOT BACK ", err);
            $scope.problem = err;
        })
    }
//    $scope.blindLogin();
}

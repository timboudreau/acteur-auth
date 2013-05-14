(function() {
    'use strict';
    angular.module('status', []).service('status', function($rootScope) {
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
    });
})();

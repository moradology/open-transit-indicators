'use strict';

angular.module('transitIndicators')
/**
 * Angular filter to set processing status string
 *
 * @param input <boolean> expects boolean value from
 *     a gtfsfeed object's is_processed value
 */
.filter('processing_status', function() {
    return function(input) {
        return input ? 'Finished Validating OTI Data' : 'Validating OTI Data';
    };
})
/**
 * Angular filter to set problem type class for rows
 * in the problem table. Each class has a color associated
 * with it via bootstrap
 *
 * @param input <string> expects OTIFeed problem type string
 *     that should either be 'war' or 'err' depending on the
 *     type of problem
 */
.filter('problem_type', function() {
    return function(input) {
        return input === 'war' ? 'warning' : 'danger';
    };
})
/**
 * Main controller for OTI upload page
 */
.controller('OTIUploadController',
    ['$scope', '$rootScope',
    function ($scope, $rootScope) {

    $scope.init = function () {
    };

}]);

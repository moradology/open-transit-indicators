'use strict';

angular.module('transitIndicators')
.controller('OTIBoundaryController',
    ['$scope', '$rootScope', '$timeout', '$upload', 'OTIConfigurationService', 'OTIBoundaryService',
    function ($scope, $rootScope, $timeout, $upload, OTIConfigurationService, OTIBoundaryService) {

    var isCityUpload = function (upload) {
        return $scope.uploadCity && upload.id === $scope.uploadCity.id;
    };

    var isRegionUpload = function (upload) {
        return $scope.uploadRegion && upload.id === $scope.uploadRegion.id;
    };

    var displayBoundaryUploadProblems = function (upload) {
        var uploadProblems = isCityUpload(upload) ?
                             $scope.uploadProblemsCity : $scope.uploadProblemsRegion;

        OTIBoundaryService.boundaryProblems.query({ boundary: upload.id }, function(data) {
            uploadProblems.warnings = _.filter(data, function (problem) {
                return problem.type === 'war' && problem.boundary === upload.id;
            });
            uploadProblems.errors = _.filter(data, function (problem) {
                return problem.type === 'err' && problem.boundary === upload.id;
            });
        });
    };

    var clearBoundaryUploadProblems = function (upload) {
        if (isCityUpload(upload)) {
            $scope.uploadProblemsCity = {
                warnings: [],
                errors: []
            };
        } else if (isRegionUpload(upload)) {
            $scope.uploadProblemsRegion = {
                warnings: [],
                errors: []
            };
        }
        setSidebarCheckmark();
    };

    $scope.$on('pollingUpload:pollingFinished', function (event, upload) {
        if (isCityUpload(upload)) {
            $scope.config.city_boundary = upload.id;
        } else if (isRegionUpload(upload)) {
            $scope.config.region_boundary = upload.id;
        }
        $scope.config.$update({ id: $scope.config.id }).then(function () {
            setSidebarCheckmark();
        });
    });

    $scope.$on('pollingUpload:processingError', function (event, upload) {
        displayBoundaryUploadProblems(upload);
    });

    $scope.$on('pollingUpload:uploadCancel', function (event, upload) {
        clearBoundaryUploadProblems(upload);
    });

    $scope.$on('pollingUpload:uploadDelete', function () {
        setSidebarCheckmark();
    });

    /*
     * Sets the sidebar checkmark if both city and region are uploaded
     */
    var setSidebarCheckmark = function () {
        var config = $scope.config;
        var checked = config ? (config.city_boundary && config.region_boundary) : false;
        $scope.setSidebarCheckmark('boundary', checked);
    };

    $scope.cityOptions = {
        uploadTimeoutMs: 5 * 60 * 1000
    };

    $scope.boundaryOptions = {
        uploadTimeoutMs: 5 * 60 * 1000
    };

    $scope.BoundaryUploads = OTIBoundaryService.boundaryUploads;
    $scope.uploadProblemsCity = {
        warnings: [],
        errors: []
    };
    $scope.uploadProblemsRegion = {
        warnings: [],
        errors: []
    };

}]);

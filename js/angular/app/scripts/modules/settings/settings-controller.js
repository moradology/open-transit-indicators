'use strict';

angular.module('transitIndicators')
.controller('OTISettingsController',
        ['$scope', 'OTISettingsService', 'config',
        function ($scope, OTISettingsService, config) {

    var setSidebarHighlight = function (viewId) {
        $scope.activeView = viewId;
    };

    $scope.setSidebarCheckmark = function (viewId, isVisible) {
        $scope.checkmarks[viewId] = !!(isVisible);
    };

    // ========================================================================
    //  OSM AND GTFS SPECIFIC
    // ========================================================================
    $scope.STATUS = OTISettingsService.STATUS;
    $scope.views = config.settingsViews;
    $scope.checkmarks = {};
    _.each($scope.views, function (view) {
        $scope.checkmarks[view.id] = false;
    });

    /**
     * Helper function to check for valid OSM import
     *
     * @param gtfsfeed <object> angular resource for gtfs feed
     */
    var checkOSMImport = function(gtfsfeed) {
        OTISettingsService.osmData.query({gtfsfeed: gtfsfeed.id}, function(osmimports) {
            // If there is an OSM import, display it status, else create one
            if (osmimports.length > 0) {
                viewOSMProblems(osmimports[0]);
                $scope.osmImport = osmimports[0];
                $scope.osmImportProgress = ($scope.osmImport.is_valid) ? $scope.STATUS.DONE : $scope.STATUS.UPLOADERROR;
            } else {
                OTISettingsService.osmData.save({gtfsfeed: $scope.gtfsUpload.id}, function(osmImport) {
                    $scope.osmImportProgress = 0;
                    pollForOSMImport(osmImport);
                });
            }
        });
    };

    /**
     * Continuously poll for OSM import status
     *
     * @param osmImport <object> angular resource for an osm import
     *
     */
    var pollForOSMImport = function (osmImport) {
        var OSMIMPORT_TIMEOUT_MS = 30 * 60 * 1000;
        var POLLING_TIMEOUT_MS = 2 * 1000;
        var startDatetime = new Date();
        var checkImport = function () {
            var nowDatetime = new Date();
            if (nowDatetime.getTime() - startDatetime.getTime() > OSMIMPORT_TIMEOUT_MS) {
                setOsmImportError('OpenStreetMap import timeout');
                $scope.osmImportProgress = $scope.STATUS.UPLOADERROR;
            } else if (osmImport.is_valid === false) {
                setOsmImportError();
                viewOSMProblems(osmImport);
            } else if (!(osmImport.is_valid && osmImport.is_processed)) {
                $scope.timeoutIdOSM = $timeout(function () {
                    osmImport = OTISettingsService.osmData.get({id: osmImport.id}, function (data) {
                        $scope.osmImport = osmImport;
                        checkImport();
                    });
                }, POLLING_TIMEOUT_MS);
            } else {
                $scope.osmImportProgress = $scope.STATUS.DONE;
                viewOSMProblems(osmImport);
            }
        };
        checkImport();
    };

    /**
     * Function to display problems of OSM import
     *
     * @param osmImport <object> angular resource for OSM import
     */
    var viewOSMProblems = function(osmImport) {
        if (!(osmImport && osmImport.id)) {
            return;
        }

        OTISettingsService.osmProblems.query(
            {osmdata: osmImport.id},
            function(data) {
                $scope.osmImportProblems.warnings = _.filter(data, function (problem) {
                    return problem.type === 'war';
                });
                $scope.osmImportProblems.errors = _.filter(data, function (problem) {
                    return problem.type === 'err';
                });
            });
    };

    /**
     * Clears the uploadProblems dict
     */
    var clearUploadProblems = function () {
        $scope.uploadProblems = {
            warnings: [],
            errors: []
        };
    };

    /**
     * Retry importing of OpenStreetMap data
     *
     */
    $scope.retryOSMImport = function() {
        if ($scope.osmImport) {
            $scope.osmImport.$delete();
            clearOsmImportProblems();
        }

        OTISettingsService.osmData.save({gtfsfeed: $scope.gtfsUpload.id}, function(osmImport) {
            $scope.osmImportProgress = 0;
            $scope.osmImport = osmImport;
            pollForOSMImport(osmImport);
        });

    };

    /**
     * Function to display problems of a gtfs feed upload
     *
     * @param upload <object> upload object that problems
     *     should be requested for
     */
    var viewProblems = function() {
        var upload = $scope.gtfsUpload;
        if (!(upload && upload.id)) {
            return;
        }

        OTISettingsService.gtfsProblems.query(
            {gtfsfeed: upload.id},
            function(data) {
                $scope.uploadProblems.warnings = _.filter(data, function (problem) {
                    return problem.type === 'war';
                });
                $scope.uploadProblems.errors = _.filter(data, function (problem) {
                    return problem.type === 'err';
                });
            });
    };


    $scope.$on('pollingUpload:pollingFinished', function () {
        viewProblems();
        $scope.retryOSMImport();
        $scope.setSidebarCheckmark('upload', true);
        $rootScope.$broadcast(OTIEvents.Settings.Upload.GTFSDone);
    });

    $scope.$on('pollingUpload:processingError', function () {
        viewProblems();
    });

    $scope.$on('pollingUpload:uploadCancel', function () {
        clearUploadProblems();
    });

    $scope.$on('pollingUpload:uploadDelete', function () {
        clearUploadProblems();
        $scope.setSidebarCheckmark('upload', false);

        // OSM imports get deleted since they have a foreign key
        // to the GTFSFeed, but we need to reset the UI
        $scope.osmImport = null;
        $scope.osmImportProgress = -1;
        $scope.osmImportProblems = {};
        $rootScope.$broadcast(OTIEvents.Settings.Upload.GTFSDelete);
    });

    // Set initial scope variables and constants
    $scope.gtfsOptions = {
        uploadTimeoutMs: 90 * 60 * 1000
    };
    $scope.GTFSUploads = OTISettingsService.gtfsUploads;
    $scope.osmImport = null;
    $scope.osmImportProblems = {};
    clearUploadProblems();

    // ========================================================================
    //  BOUNDARY SPECIFIC
    // ========================================================================


    $scope.init = function() {
        OTISettingsService.gtfs.get().then(function(data) {
            $scope.gtfsData = data;
        });
        //OTISettingsService.osm.get().then(function(data) {
        //    $scope.osmData = data;
        //});
        OTISettingsService.cityBoundary.get().then(function(data) {
            $scope.cityBoundary = data;
        });
        OTISettingsService.regionBoundary.get().then(function(data) {
            $scope.regionBoundary = data;
        });
        OTISettingsService.demographics.get().then(function(data) {
            $scope.demographicsData = data;
        });
        OTISettingsService.config.get().then(function(data) {
            $scope.configData = data;
        });
        OTISettingsService.realtime.get().then(function(data) {
            $scope.realtimeData = data;
        });
        OTISettingsService.demographics.metrics().then(function(data) {
            $scope.test = data
        })
    }


    var init = function() {
        // GTFS
        $scope.gtfsData = function(gtfsData) {
            var validGtfs = _.filter(gtfsData, function(upload) {
                return upload.is_processed && upload.is_valid
            });
            $scope.checkmarks['upload'] = validGtfs.length > 0
            $scope.gtfsUpload = validGtfs[0]

            // OSM
            checkOSMImport(validGtfs[0]);
            return gtfsData;
        };

    }

    $scope.$on('$stateChangeSuccess', function (event, toState) {
       setSidebarHighlight(toState.name);
    });

}]);

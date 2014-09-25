'use strict';

/**
 * Service responsible for getting data on uploads/problems
 * for GTFS data
 */
angular.module('transitIndicators')
.factory('OTISettingsService', ['$resource', '$q', '$http', function($resource, $q, $http) {
    var settingsService = {};

    // Urls used throughout the 'settings' portion of OTI
    var _settings_urls = {
      gtfs: '/api/gtfs-feeds/:id',
      gtfsProblems: '/api/gtfs-feed-problems/:id',
      osm: '/api/osm-data/:id',
      osmProblems: '/api/osm-data-problems/:id',
      boundaries: '/api/boundaries/:id',
      boundaryProblems: '/api/boundary-problems/:id',
      samplePeriod: '/api/sample-periods/:type/ ',
      demographics: '/api/demographics/:id/ ',
      demogProblems: '/api/demographics-problems/:id/ ',
      demogConfig: '/api/config-demographic/ ',
      realtime: '/api/real-time/:id/ ',
      realtimeProblems: '/api/real-time-problems/:id/ ',
      config: '/api/config/:id/ '
    }

    var rsc = {}
    // Resources to be used throughout settings
    // GTFS resources
    rsc.gtfsUploads = $resource(_settings_urls.gtfs, {}, {
        'update': {
            method: 'PATCH',
            url: _settings_urls.gtfs
        }
    });
    rsc.gtfsProblems = $resource(_settings_urls.gtfsProblems);

    rsc.osmData = $resource(_settings_urls.osm, {id: '@id'}, {
        'save': {
            method: 'POST',
            url: _settings_urls.osm
        }
    });
    rsc.osmProblems = $resource(_settings_urls.osmProblems);

    // Boundary resources
    rsc.boundaryUploads = $resource(_settings_urls.boundaries, {}, {
        update: {
            method: 'PATCH',
            url: _settings_urls.boundaries
        }
    })
    rsc.boundaryProblems = $resource(_settings_urls.boundaryProblems);


    // Demographics resources
    rsc.demographics = $resource(_settings_urls.demographics, {}, {
        'update': {
            method: 'PATCH',
            url: _settings_urls.demographics
        },
        // After a load POST, is_valid == true and is_loaded == true if success
        // otherwise, is_valid reverts to false
        'load': {
            method: 'POST',
            url: '/api/demographics/:id/load/ ',
            params: {
                population_metric_1: '@population_metric_1',
                population_metric_2: '@population_metric_2'
            }
        }
    });
    rsc.demogProblems = $resource(_settings_urls.demogProblems);
    rsc.demogConfigs = $resource(_settings_urls.demogConfig);

    // Real-time (i.e. observed) resources
    rsc.realtimes = $resource(_settings_urls.realtime, {}, {
        'update': {
            method: 'PATCH',
            url: _settings_urls.realtime
        }
    });
    rsc.realtimeProblems = $resource(_settings_urls.realtimeProblems)

    // User entered config resources
    rsc.configs = $resource(_settings_urls.config)
    rsc.samplePeriods = $resource(_settings_urls.samplePeriod, {type: '@type'}, {
        update: {
            method: 'PUT',
            url:_settings_urls.samplePeriod
        }
    });

    /*settingsService.checkStatus = {}
    settingsService.checkStatus.gtfs = function() {
        return $http({method: 'GET', url: '/api/gtfs-feeds'})
    }
    settingsService.checkStatus.config = function() {
            config: $http({method: 'GET', url: '/api/config'})
    }*/

    settingsService.STATUS = {
        START: -1,
        UPLOADERROR: -2,
        PROCESSING: 100,
        DONE: 101
    };


    settingsService.cache = {
        has:  function(cacheName) { return (this[cacheName] !== undefined) },
        gtfs: undefined,
        osm: undefined,
        cityBoundary: undefined,
        regionBoundary: undefined,
        demographics: undefined,
        realtime: undefined,
        samplePeriod: undefined,
        config: undefined
    }

    settingsService.gtfs = {
        update: function() {
            return rsc.gtfsUploads.query().$promise.then(function(gtfsData) {
                var deferred = $q.defer();
                var validGtfs = _.filter(gtfsData, function(upload) {
                    return upload.is_processed && upload.is_valid
                });
                deferred.resolve(validGtfs[0])
                settingsService.cache.gtfs = deferred.promise;
                return deferred.promise;
            });
        },
        get: function() {
            if (settingsService.cache.has('gtfs')) {
                return settingsService.cache.gtfs;
            } else {
                return this.update();
            }
        }
    }
    settingsService.osm = {
        progress: 0,
        update: function() {
            $q.when(settingsService.gtfs.get()).then(function(gtfs) {
                return rsc.osmData.query({gtfsfeed: gtfs.id}).$promise
                  .then(function(osmimports) {
                      var deferred = $q.defer()
                      // If there is an OSM import, display it status, else create one
                      if (osmimports.length > 0) {
                          viewOSMProblems(osmimports[0]);
                          this.progress = (osmimports[0].is_valid) ? 'DONE' : 'UPLOADERROR';
                          deferred.resolve(osmimports[0]);
                          settingsService.cache.osm = deferred.promise;
                          return deferred.promise;
                      } else {
                          OTISettingsService.osmData.save({gtfsfeed: $scope.gtfsUpload.id}, function(osmImport) {
                              this.progress = 0;
                              pollForOSMImport(osmImport);
                          });
                      }
                  });
            });
        },
        get: function() {
            if (settingsService.cache.has('osm')) {
                return settingsService.cache.osm;
            } else {
                return this.update();
            }
        }
    }
    settingsService.config = {
        update: function() {
            return rsc.configs.query().$promise.then(function(config) {
                var deferred = $q.defer()
                if (config.length !== 1) {
                    console.error('Expected a single configuration but found: ', config.length);
                    return;
                } else {
                    deferred.resolve(config[0])
                    settingsService.cache.config = deferred.promise;
                    return deferred.promise;
                }
            });
        },
        get: function() {
            if (settingsService.cache.has('config')) {
                return settingsService.cache.config;
            } else {
                return this.update();
            }
        }
    }
    settingsService.cityBoundary = {
        update: function() {
            return settingsService.config.get().then(function(config) {
                return rsc.boundaryUploads.get({ id: config.city_boundary }).$promise
                .then(function(boundary) {
                    var deferred = $q.defer();
                    deferred.resolve(boundary)
                    settingsService.cache.cityBoundary = deferred.promise;
                    return deferred.promise;
                });
            });
        },
        get: function() {
            if (settingsService.cache.has('cityBoundary')) {
                return settingsService.cache.cityBoundary;
            } else {
                return this.update();
            }
        }
    }
    settingsService.regionBoundary = {
        update: function() {
            return settingsService.config.get().then(function(config) {
                return rsc.boundaryUploads.get({ id: config.region_boundary }).$promise
                .then(function(boundary) {
                    var deferred = $q.defer();
                    deferred.resolve(boundary);
                    settingsService.cache.regionBoundary = deferred.promise;
                    return deferred.promise;
                });
            });
        },
        get: function() {
            if (settingsService.cache.has('regionBoundary')) {
                return settingsService.cache.regionBoundary;
            } else {
                return this.update();
            }
        }
    }
    settingsService.demographics = {
        update: function() {
            return rsc.demographics.query().$promise.then(function(demographics) {
                var deferred = $q.defer();
                deferred.resolve(demographics[0]);
                settingsService.cache.demographics = deferred.promise;
                return deferred.promise;
            });
        },
        get: function() {
            if (settingsService.cache.has('demographics')) {
                return settingsService.cache.demographics;
            } else {
                return this.update();
            }
        },
        metrics: function() {
            var deferred = $q.defer();
            deferred.resolve({
                pop_metric_1: this.get().pop_metric_1_field || null,
                pop_metric_2: this.get().pop_metric_2_field || null,
                dest_metric_1: this.get().dest_metric_1_field || null
            });
            return deferred.promise;
        }
    }
    settingsService.realtime = {
        update: function() {
            return rsc.realtimes.query().$promise.then(function(realtimes) {
                var deferred = $q.defer()
                var validRealtime = _.filter(realtimes, function(upload) {
                    return upload.is_processed && upload.is_valid
                });
                deferred.resolve(realtimes[0])
                settingsService.cache.realtime = deferred.promise;
                return deferred.promise;
            });
        },
        get: function() {
            if (settingsService.cache.has('realtime')) {
                return settingsService.cache.realtime;
            } else {
                return this.update();
            }
        }
    }
    settingsService.samplePeriod = {
        update: function() {
            return rsc.samplePeriods.query().$promise.then(function(periods) {
                var deferred = $q.defer();
                deferred.resolve(periods[0]);
                settingsService.cache.samplePeriod = deferred.promise;
                return deferred.promise;
            });
        },
        get: function() {
            if (settingsService.cache.has('samplePeriod')) {
                return settingsService.cache.samplePeriod;
            } else {
                return this.update();
            }
        }
    }















    return settingsService;
}]);

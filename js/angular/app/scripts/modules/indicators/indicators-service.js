'use strict';

angular.module('transitIndicators')
.factory('OTIIndicatorsService',
        ['$q', '$http', '$resource', 'OTISettingsService', '$rootScope',
        function ($q, $http, $resource, OTISettingsService, $rootScope) {

    var otiIndicatorsService = {};
    var nullJob = 0;
    var modalStatus = false;
    otiIndicatorsService.selfCityName = null;

    otiIndicatorsService.setModalStatus = function (isOpen) {
        modalStatus = isOpen;
    };

    otiIndicatorsService.isModalOpen = function () {
        return modalStatus;
    };

    otiIndicatorsService.Indicator = $resource('/api/indicators/:id/', {id: '@id'}, {
        'update': {
            method: 'PATCH',
            url: '/api/indicators/:id/'
        }
    }, {
        stripTrailingSlashes: false
    });

    /**
     * This is here rather than as a 'search' method on Indicator because the function refused to
     *  call the success/failure callbacks on function return as detailed here:
     *  HTTP GET "class" actions: Resource.action([parameters], [success], [error])
     * My guess is the redirect from the stripTrailingSlash is at work again...
     * TODO: Filter out route aggregations in request
     */
    otiIndicatorsService.query = function (method, config) {
        var dfd = $q.defer();
        $http({
            method: method,
            url: '/api/indicators/',
            params: config
        }).success(function (data) {
            dfd.resolve(data);
        }).error(function (error) {
            console.error('otiIndicatorsService.search():', error);
            dfd.resolve([]);
        });
        return dfd.promise;
    };

    /**
     * Thin wrapper for Indicator used in the controller for setting the map properties
     */
    otiIndicatorsService.IndicatorConfig = function (config) {
        this.calculation_job = config.calculation_job || nullJob;
        this.type = config.type;
        this.sample_period = config.sample_period;
        this.aggregation = config.aggregation;
        this.modes = $rootScope.visibleModes || '';
    };

    /**
     * Get the current indicator calculation job
     *
     * @param callback: function to call after request is made, has a single argument 'calculation_job'
     */
    otiIndicatorsService.getIndicatorCalcJob = function (callback) {
        var promises = []; // get the city name before using it to filter indicator CalcJobs
        promises.push(OTISettingsService.cityName.get({}, function (data) {
            otiIndicatorsService.selfCityName = data.city_name;
        }));

        promises.push($http.get('/api/indicator-calculation-job/').success(function () {
        }).error(function (error) {
            console.error('getIndicatorCalcJob:', error);
            callback(nullJob);
        }));

        $q.all(promises).then(function (data) {
            var job = nullJob;
            // flatter is better - the following (very long) line just ensures the existence of
            // certain nodes which are operated on in the flow
            if (data && data[1] && data[1].data && data[1].data.current_jobs &&
                !_.isEmpty(data[1].data.current_jobs) &&
                _.findWhere(data[1].data.current_jobs, {calculation_job__city_name: otiIndicatorsService.selfCityName})) {
                var jobs = data[1].data;
                var jobObj = _.findWhere(jobs.current_jobs, {calculation_job__city_name: otiIndicatorsService.selfCityName});
                job = jobObj.calculation_job;
                callback(job);
                return; // otherwise fall through to set null job
            }
            callback(nullJob);
        }, function (error) {
            console.log('otiIndicatorsService.getIndicatorCalcJob error:');
            console.log(error);
        });
    };

    otiIndicatorsService.getIndicatorDescriptionTranslationKey = function(key) {
        return 'INDICATOR_DESCRIPTION.' + key;
    };

    return otiIndicatorsService;
}]);

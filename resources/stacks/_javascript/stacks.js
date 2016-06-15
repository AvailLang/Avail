var stacksApp = angular.module('stacksApp',['ngSanitize','ui.router']);
var $stateProviderRef;

stacksApp.config(['$stateProvider', '$urlRouterProvider', function ($stateProvider,$urlRouterProvider) {

	//default to stacks route
	$urlRouterProvider.otherwise('/');

	//hack to get around not being able to inject $urlMatcherFactory into the config - it needs to be a provider to do this
	$stateProviderRef = $stateProvider;

    $stateProvider
    .state('stacks', {
        url:'/',
        templateUrl: 'templates/stacks.html',
        controller: 'StacksCtrl'
    })
    .state('stacks.main', {
    	url: 'main/',
    	templateUrl: 'landing-detail.html'
    });

}]);

stacksApp.run(['$urlMatcherFactory', function($urlMatcherFactory) {

	var urlMatcher = $urlMatcherFactory.compile("/method/*path");
	var urlMatcher2 = $urlMatcherFactory.compile("/direct/*path");

	$stateProviderRef.state('stacks.method', {
    	url: urlMatcher,
    	templateUrl: 'templates/method.html',
    	controller: 'StacksMethodCtrl'	
    }).state('direct', {
    	url: urlMatcher2,
    	templateUrl: 'templates/method.html',
    	controller: 'StacksMethodCtrl'	
    });



}]);

stacksApp.controller('StacksCtrl',['$scope','$http','$window', function($scope,$http,$window) {

    //The links to Avail methods and classes organized by Category
	var categoriesURL = "/json/categories.json"
	$scope.categories = {
		content: []
	};

	$http.get(categoriesURL).then(function (response) {
		$scope.categories.content = response.data.categories;
		//console.log($scope.categories.content)
	});

    //The JSON file that manages internal linking between internal documentation content
    //to other methods and classes
	$scope.jsonURL = "/json/internalLink.json";
	$scope.methodLinkage = {};
	$http.get($scope.jsonURL).then(function (response) {
		$scope.methodLinkage = response.data;
		//console.log($scope.methodLinkage);
	});

    //JSON file containing all the category comment descriptions parsed
    //from category comments. - TODO build UI to populate this information dynamically
    var categoriesDescriptionsURL = "/json/categoriesDescriptions.json"
	$scope.categoriesDescriptions = {
		content: []
	};

	$http.get(categoriesDescriptionsURL).then(function (response) {
		$scope.categoriesDescriptions.content = response.data.categories;
		//console.log($scope.categories.content)
        //REMOVE ME!!
        window.categoryDescriptions = response.data.categories;
	});

    //JSON file containing all the module comment descriptions parsed
    //from category comments. - TODO build UI to populate this information dynamically
    var moduleDescriptionsURL = "/json/moduleDescriptions.json"
	$scope.moduleDescriptions = {
		content: []
	};

	$http.get(moduleDescriptionsURL).then(function (response) {
		$scope.moduleDescriptions.content = response.data.modules;
		//console.log($scope.categories.content)
        //REMOVE ME!!
        window.moduleDescriptions = response.data.modules;
	});

	// !!! TESTING !!!
	$scope.goToMethod = function(link) {

		var url = 'index.html#/method/'+link;
		//about-avail/documentation/stacks/library-documentation/avail/Avail/IO/Files/Primitives/2623497882.json
		$window.location = url;
	}

	$scope.goToDirectMethod = function() {
		
		var temp = $window.location.hash.split('/');
		var url = '';
		if (temp[1] == 'method') {
			temp[1] = 'direct';
			url = 'index.html'+temp.join('/');
			$window.location = url;
		}
		
	}

	$scope.search = '';
	$scope.filterOnCategory = function(content) {
    	return (((content.category.toLowerCase()).indexOf($scope.search.toLowerCase()) >= 0) || content.selected);
	};

	$scope.methodList = [];
	$scope.methodListUpdate = function()
	{
		var allCategories = $scope.categories;
		var filteredMethods = {};
		var finalList = {content : []};
		var selectedCount = 0;
		for (var i=0; i < allCategories.content.length;i++)
		{
			if (allCategories.content[i].selected)
			{
				selectedCount++;
				for  (var j = 0; j < allCategories.content[i].methods.length; j++)
				{
					var method = allCategories.content[i].methods[j].distinct;
					try
					{
						var newCount = filteredMethods[method].count + 1;
						filteredMethods[method].count = newCount;
					}
					catch(err)
					{
						filteredMethods[method] =
							{"name" :allCategories.content[i].methods[j].methodName,
							"link" : allCategories.content[i].methods[j].link,
							"distinct" : allCategories.content[i].methods[j].distinct,
							"count" : 1};
					}
				}
			}
		}
		for (var key in filteredMethods)
		{
			if (filteredMethods[key].count == selectedCount)
			{
				finalList.content.push({"name" : filteredMethods[key].name, "link" : filteredMethods[key].link, distinct: key})
			}
		}

		$scope.methodList = finalList;
	}; //end methodListUpdate


	$scope.methodLinkagePairedDown = {};
	$scope.methodSearch = '';
	$scope.customStyle = "";
	$scope.getInternalLink = function(aMethod) {
	
		if (aMethod.length > 0)
		{
			if ($scope.strictSearch)
			{
				if (aMethod in $scope.methodLinkage)
				{
					$scope.methodLinkagePairedDown={};
					$scope.methodLinkagePairedDown[aMethod] = $scope.methodLinkage[aMethod];
					$scope.customStyle = "";
				}
				else 
				{					
					$scope.methodLinkagePairedDown={};	
					//$scope.methodLinkagePairedDown["Method Not Found"] = document.getElementById('api_frame').contentWindow.location.href;
					$scope.methodLinkagePairedDown["Method Not Found"] = $scope.linkValue;
					$scope.customStyle = "font-style:italic";
				}
			}
			else
			{
				var tempList = {};
				$scope.methodLinkagePairedDown={};
				for (var key in $scope.methodLinkage)
				{
					if((key.toLowerCase()).indexOf(aMethod.toLowerCase()) >=0)
					{
						tempList[key] = $scope.methodLinkage[key];
						$scope.customStyle = "";
					}
				}
				$scope.methodLinkagePairedDown = tempList;

			}
		}
		console.log($scope.methodLinkagePairedDown);
	}; // getInternalLink

	$scope.scrollTo = function(id) {
		$location.hash(id);
		$anchorScroll();
	}  
		
	

}]);

stacksApp.controller('StacksMethodCtrl',['$scope','$stateParams','$http', function($scope,$stateParams,$http) {

	var jsonURL = $stateParams.path;
	console.log(jsonURL);
	//load a JSON file here
	$http.get(jsonURL).success(function(response) {
		$scope.methodJson = angular.fromJson(response); //deserialize
		console.log($scope.methodJson);
	});

}]);

stacksApp.directive('ngEnter', function() {
        return function(scope, element, attrs) {
            element.bind("keydown keypress", function(event) {
                if(event.which === 13) {
                    scope.$apply(function(){
                        scope.$eval(attrs.ngEnter, {'event': event});
                    });
                    event.preventDefault();
                }
            });
        };
    });

stacksApp.directive('parseMethodJson',['$window', function($window) {

	return {

		restrict: "A",
		templateUrl: "templates/method.template.html",
		scope: {
			json: "=parseMethodJson"
		},
		link: function(scope,element,attrs) {

			scope.semanticRestriction = false;
			scope.grammaticalRestriction = false;
			scope.type = "";

			//need to watch for async data load
			scope.$watch(function() {
				return scope.json;
				},
			function() {
				if (scope.json) { //only if it has loaded

					if (scope.json.type == "method" || scope.json.type == "macro") {
						scope.type = "method";
					}
					else if (scope.json.type == "class") {
						scope.type = "class";
						for (var i=0; i<scope.json.sees.length;i++) {
							if (scope.json.sees[i][0] == "h") {
								var tempUrl = scope.json.sees[i];
								var tempHtml = '<a href="'+tempUrl+'">'+tempUrl+'</a>';
								scope.json.sees[i] = tempHtml;
							}
						}
					}
					else {
						scope.type = "ambiguous";
					}

					if (scope.json.hasOwnProperty("semanticRestrictions")) {
						scope.semanticRestriction = true;
					}

					if (scope.json.hasOwnProperty("grammaticalRestrictions")) {
						scope.grammaticalRestriction = true;
					}
				}
			})
			
		} // end link

	} // end return

}]);

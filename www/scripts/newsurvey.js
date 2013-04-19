function SurveyEditor($scope, $http) {

    $scope.answerTypes = [
        {name: 'Multiple choice', type: 'multiplechoice'},
        {name: 'Number', type: 'number'},
        {name: 'True/False', type: 'truefalse'},
        {name: 'Text', type: 'text'},
    ]

    $scope.survey = {name: 'thingy',
        description: 'How is your arthritis',
        _id: null,
        questions: []
    };
    function prototypeQuestion() {
        switch ($scope.questionType) {
            case 'truefalse' :
                return {description: 'Do you have hands',
                    help: 'Help help',
                    answerType:
                            {type: 'truefalse',
                                id: 'hands',
                                constraints: [{type: 'required'}]}};
            case 'text' :
                return {description: 'How do you feel?',
                    help: 'Feelings, nothing more than feelings',
                    answerType:
                            {type: 'text',
                                id: 'feel',
                                constraints:
                                        [{type: 'max-length', length: 20}]}}
            case 'number' :
                return {description: 'How old are you?',
                    help: 'Age help',
                    answerType:
                            {type: 'number',
                                id: 'age',
                                constraints:
                                        [{type: 'non-negative'},
                                            {type: 'allowed-range', min: 5, max: 120}]}}
            case 'multiplechoice' :
                return {description: 'How is your arthritis?',
                    help: 'Help',
                    answerType:
                            {type: 'multiplechoice',
                                id: 'identifier',
                                constraints: [],
                                answers: ['Bad', 'Good', 'Purple']}}

        }
    }
    $scope.questionType = 'text';
    $scope.prototypeQuestion = prototypeQuestion;
    $scope.addQuestion = function() {
        $scope.survey.questions.push(prototypeQuestion());
    }
    $scope.addMultipleChoiceAnswer = function(question) {

    }
    $scope.save = function() {
        $scope.loading = true;
        console.log("WILL POST \n" + JSON.stringify($scope.survey));
        $http.post('/time/users/tim/surveys', $scope.survey).success(function(saved) {
            console.log("SAVED ", saved);
            $scope.loading = false;
            $scope.survey = saved;
        }).error(function(err) {
            $scope.problem = err;
            $scope.loading = false;
        })
    }
    $scope.addQuestion();
}

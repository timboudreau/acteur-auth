function SurveyEditor($scope, $http, user, status, urls) {

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

    $scope.constraints = [];
    function ConstraintInfo() {
        this.minMaxEnable = false;
        this.nonNegative = false;
        this.maxLength = 140;
        this.required = true;
        this.min = 0;
        this.max = 100;
    }

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
    $scope.questionType = 'multiplechoice';
    $scope.prototypeQuestion = prototypeQuestion;

    $scope.addQuestion = function() {
        var q = prototypeQuestion();
        $scope.constraints.push(new ConstraintInfo());
        $scope.survey.questions.push(q);
        console.log('SURVEY:', $scope.survey)
    }

    $scope.deleteAnswer = function(questionIndex, answerIndex) {
        var nue = [];
        var a = $scope.survey.questions[questionIndex].answerType.answers;
        for (var i = 0; i < a.length; i++) {
            if (i !== answerIndex) {
                nue.push($scope.survey.questions[questionIndex].answerType.answers[i]);
            }
        }
        $scope.survey.questions[questionIndex].answerType.answers = nue;
    }

    $scope.addMultipleChoiceAnswer = function(ix, c) {
        if (!$scope.survey.questions[ix].answerType.answers) {
            $scope.survey.questions[ix].answerType.answers = [];
        }
        console.log('C is ' + c)
        $scope.survey.questions[ix].answerType.answers.push(c);
        console.log('SURVEY:', $scope.survey.questions[ix])
    }

    $scope.save = function() {
        var surv = angular.copy($scope.survey);
        for (var i = 0; i < surv.questions.length; i++) {
            var q = surv.questions[i];
            var con = $scope.constraints[i];
            q.answerType.constraints = [];
            if (con.required) {
                q.answerType.constraints.push({type: 'required'});
            }
            switch (q.answerType.type) {
                case 'multiplechoice' :
                    break;
                case 'number':
                    if (con.minMaxEnable) {
                        q.answerType.constraints.push({type: 'allowed-range', min: con.min, max: con.max})
                    }
                    if (con.nonNegative) {
                        q.answerType.constraints.push({type: 'non-negative'})
                    }
                    // fall through
                default :
                    if (q.answerType.answers) {
                        delete q.answerType.answers;
                    }
            }
        }
        
        console.log('WILL SEND ', surv)
//        $http.post(API_BASE + 'users/' + user.name + '/surveys', surv).success(function(saved) {
        $http.post(urls.userPath(user.name, 'surveys'), surv).success(function(saved) {
            status.success = "Saved";
            $scope.survey = saved;
        }).error(status.errorHandler)
    }
    $scope.addQuestion();
}

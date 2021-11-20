'use strict';
var AWS = require('aws-sdk');
var response = require('cfn-response');
var roleName = 'EMR_EC2_DefaultRole';
exports.handler = (event, context, callback) => {
    var iam = new AWS.IAM();
    var params = {InstanceProfileName: roleName};
    iam.deleteInstanceProfile(params, function (err, data) {
        var params = {InstanceProfileName: roleName};
        iam.createInstanceProfile(params, function (err, data) {
            if (err) {
                console.log(err, err.stack);
                response.send(event, context, response.FAILED, err);
            } else {
                var params = {InstanceProfileName: roleName, RoleName: roleName};
                iam.addRoleToInstanceProfile(params, function (err, data) {
                    if (err) {
                        console.log(err, err.stack);
                        response.send(event, context, response.FAILED, err);
                    } else {
                        console.log(data);
                        response.send(event, context, response.SUCCESS, data);
                    }
                });
            }
        });
    });
}
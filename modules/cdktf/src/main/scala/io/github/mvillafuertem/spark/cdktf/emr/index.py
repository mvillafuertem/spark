import json
import boto3
import urllib3
import logging
http = urllib3.PoolManager()
logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    logger.info(event)
    client = boto3.client('ec2')
    """ Get VPC list """
    response = client.describe_vpcs()
    vpclist = response.get('Vpcs')

    """ Flag to track if the default vpc exists """
    hasdefault = False

    """ Iterate over dict of VPCs and check to see if we have a default VPC """
    for vpcs in vpclist:
        """ Search for IsDefault value in vpcs list """
        if 'IsDefault' in vpcs:
            vpcname = vpcs.get("VpcId")
            print ("VPC: %s" % vpcname)
            isd = vpcs.get("IsDefault")
            print ("IsDefault value: %s" % isd)
            if isd:
                """ Region has the default vpc - set flag to True """
                hasdefault = True
                break

    print ("Has default: %s" % hasdefault)
    """ Create default VPC and associated networking """
    if not hasdefault:
        print ("We need to create the default vpc")
        try:
            response = client.create_default_vpc()
            print (response)
        except Exception as error:
            print (error)
    response_value = int(event['ResourceProperties']['Input']) * 5
    response_data = {}
    response_data['Data'] = response_value
    send_response(event, context, "SUCCESS", "Default VPC Created", response_data)

def send_response(event, context, status, reason, data):
    body = json.dumps({
        "Status": status,
        "Reason": reason,
        "PhysicalResourceId": context.log_stream_name,
        "StackId": event.get("StackId"),
        "RequestId": event.get("RequestId"),
        "LogicalResourceId": event.get("LogicalResourceId"),
        "NoEcho": False,
        "Data": data
    })
    http.request(
        "PUT",
        event.get("ResponseURL"),
        body=body,
        headers={
            "Content-Type": "",
            "Content-Length": str(len(body))
        }
    )

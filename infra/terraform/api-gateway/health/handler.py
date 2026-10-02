import json


def lambda_handler(event, context):
    return {
        "statusCode": 200,
        "headers": {"content-type": "application/json; charset=utf-8"},
        "body": json.dumps({
            "status": "ok",
            "component": "flashstock-api-gateway",
            "backend_connected": False,
            "message": "HTTP API operativa; integraciones de microservicios pendientes"
        }),
    }

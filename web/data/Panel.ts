import type { WebApiClient } from "@shirokasoke/webapi-sdk"
import Joi from "joi"

type allowed_method = keyof WebApiClient

export interface Panel<T, req = any> {
    title: string
    method: allowed_method
    dataKey: (requestData?: req) => string
    requestData?: req
    dafaultData: T
    joi?: Joi.AnySchema<T>
    func?: (api: WebApiClient, requestData?: req) => Promise<T>
    size: {
        w: number, h: number, minW?: number, minH?: number, maxW?: number, maxH?: number
    }
    Render: (data: T, requestData?: req) => React.ReactElement
}
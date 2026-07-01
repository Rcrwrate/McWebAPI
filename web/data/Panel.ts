import type { WebApiClient } from "@shirokasoke/webapi-sdk"
import Joi from "joi"

type allowed_method = keyof WebApiClient

export interface Panel<T> {
    title: string
    method: allowed_method
    dataKey: string
    requestData?: any
    dafaultData: T
    joi?: Joi.AnySchema<T>
    func?: (api: WebApiClient, requestData: any) => Promise<T>
    size: { w: number, h: number }
    Render: (data: T) => React.ReactElement
}

abstract class panel<T> {
    abstract title: string
    abstract method: allowed_method
    abstract dataKey: () => string
    abstract requestData?: any
    abstract dafaultData: T
    abstract joi?: Joi.AnySchema<T>
    abstract func?: (api: WebApiClient, requestData: any) => Promise<T>
    abstract size: { w: number, h: number }
    abstract Render: (data: T) => React.ReactElement
}
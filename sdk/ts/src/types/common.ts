export interface ClassInfo {
    package?: string;
    location?: string;
    extends?: string[];
    implements?: string[];
}

export interface Coordinates {
    posX: number;
    posY: number;
    posZ: number;
    dimension: number;
}

export interface ApiSuccessResponse<T> {
    success: true;
    data: T;
}

export interface ApiErrorResponse {
    success: false;
    message: string;
    /** 路由描述 */
    desc?: string;
    stack?: string | null;
    /**
     * 失败时附带的业务数据
     */
    body?: unknown;
}

export type ApiResponse<T> = ApiSuccessResponse<T> | ApiErrorResponse;

export interface RootInfo {
    modid: string;
    version: string;
    buildTime?: number;
}

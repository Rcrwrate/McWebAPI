'use client';
import { LicenseInfo } from '@mui/x-license';
import { muiXTelemetrySettings } from '@mui/x-license';

muiXTelemetrySettings.disableTelemetry();
//@ts-ignore
globalThis.__MUI_X_TELEMETRY_DISABLED__ = true;
LicenseInfo.setLicenseKey('30f25b01ad61f4588761' + 'be6a45c66da4Tz1tYXN0ZXItbXVpLW5ldGxpZnktc2hvd2N' + 'hc2UsRT0xODcwMTgwMjMxOTIxLFM9cHJlbWl1bSxMTT1wZXJwZXR1YWwsUFY9UTMtMjAyNCxLVj' + '0y');

export default function MUI() {
    return null;
}

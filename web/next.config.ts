import type { NextConfig } from "next";

const nextConfig: NextConfig = {
    output: 'export',
    allowedDevOrigins: ['*.cnb.run', '127.0.0.1'],
    productionBrowserSourceMaps: true
};

export default nextConfig;

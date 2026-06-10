import type { Metadata } from 'next'
import { Heads } from './Head'
import { Suspense } from "react";
import Loading from './loading';
import Snackbar from './Snackbar';
import { Root } from './Drawer';
import BackDropProvider from '@/components/BackDrop';
import { APIProvider } from '@/data/api';

export const metadata: Metadata = {
    title: 'GTNH WebAPI',
    description: 'GTNH WebAPI',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
    return <html lang="zh">
        <head>
            <Heads />
        </head>
        <body style={{ margin: "auto" }}>
            <Root>
                <BackDropProvider>
                    <APIProvider>
                        <Snackbar>
                            {children}
                        </Snackbar>
                    </APIProvider>
                </BackDropProvider>
            </Root>
        </body>
    </html>;
}

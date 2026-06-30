import BackDropProvider from '@/components/BackDrop';
import MUI from '@/components/MUI';
import { APIProvider } from '@/data/api';
import type { Metadata } from 'next';
import { Root } from './Drawer';
import { Heads } from './Head';
import Snackbar from './Snackbar';


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
            <MUI />
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

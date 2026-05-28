"use client"
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import HomeIcon from '@mui/icons-material/Home';
import LoginIcon from '@mui/icons-material/Login';
import LoyaltyIcon from '@mui/icons-material/Loyalty';
import MenuIcon from '@mui/icons-material/Menu';
import SearchIcon from '@mui/icons-material/Search';
import SettingsIcon from '@mui/icons-material/Settings';
import StarIcon from '@mui/icons-material/Star';
import StorageIcon from '@mui/icons-material/Storage';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import WhatshotIcon from '@mui/icons-material/Whatshot';
import { AppBar, Box, Divider, Drawer, IconButton, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Slide, SwipeableDrawer, Toolbar, Typography, useScrollTrigger } from '@mui/material';
import { ThemeProvider } from '@mui/material/styles';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ReactNode, useState } from 'react';
import { useTheme } from './Theme';

const DRAWER_WIDTH = 240;

const LINKS = [
    { text: 'Home', href: '/i', icon: HomeIcon },
    { text: 'Blocks', href: '/blocks', icon: ViewModuleIcon },
    { text: 'AE', href: '/ae', icon: StorageIcon },
    { text: 'Search', href: '/search', icon: SearchIcon },
    { text: 'Popular', href: '/popular', icon: WhatshotIcon },
    { text: 'Subscription', href: '/watched', icon: LoyaltyIcon },
    { text: 'Favourite', href: '/favorites', icon: StarIcon },
];

const PLACEHOLDER_LINKS = [
    { text: 'Settings', href: '/settings', icon: SettingsIcon },
    
    { text: 'Login', href: '/login', icon: LoginIcon },
    // { text: 'Logout', href: '/api/auth/signout', icon: LogoutIcon },
];

export function Root({ children }: { children?: ReactNode }) {
    const [open, setOpen] = useState(false);
    const [dark, setdark] = useState(typeof window !== "undefined" ? window.localStorage.dark == "true" : false);
    const router = useRouter()
    const theme = useTheme()
    const trigger = useScrollTrigger({ target: typeof window !== "undefined" ? window : undefined });

    return <ThemeProvider theme={theme}>
        <Slide appear={false} direction="down" in={!trigger}>
            <AppBar position="fixed" sx={{ zIndex: 2000, minHeight: '64px' }} color='inherit'>
                <Toolbar sx={{ minHeight: '64px', color: (theme) => theme.palette.text.primary }}>
                    <IconButton
                        size="large"
                        edge="start"
                        aria-label="open drawer"
                        sx={{ mr: 2 }}
                        onClick={() => { setOpen(!open) }}
                        color="inherit"
                    >
                        <MenuIcon />
                    </IconButton>
                    <Typography variant="h6" noWrap component="div" onClick={() => { router.push("/") }}>
                        GTNH WebAPI
                    </Typography>
                    <Box sx={{ flexGrow: 1 }} />
                    <IconButton sx={{ ml: 1 }} onClick={() => {
                        localStorage.setItem("dark", `${!dark}`)
                        setdark(!dark)
                    }}>
                        {theme.palette.mode === 'dark' ? <Brightness7Icon /> : <Brightness4Icon sx={{ color: (theme) => theme.palette.text.primary }} />}
                    </IconButton>
                    {/* </MenuItem> */}
                </Toolbar>
            </AppBar>
        </Slide>
        <SwipeableDrawer onClose={() => { setOpen(false) }} onOpen={() => { setOpen(true) }} open={open}>
            <Drawer
                sx={{
                    width: DRAWER_WIDTH,
                    flexShrink: 0,
                    '& .MuiDrawer-paper': {
                        width: DRAWER_WIDTH,
                        boxSizing: 'border-box',
                        top: ['56px', '64px'],
                        height: 'auto',
                        bottom: 0,
                    },
                    // display: { xs: 'none', sm: 'block' },
                }}
                variant="permanent"
                anchor="left"
                open={open}
            >
                <Divider />
                <List>
                    {LINKS.map(({ text, href, icon: Icon }) => (
                        <ListItem key={href} disablePadding>
                            <ListItemButton component={Link} href={href}>
                                <ListItemIcon>
                                    <Icon />
                                </ListItemIcon>
                                <ListItemText primary={text} />
                            </ListItemButton>
                        </ListItem>
                    ))}
                </List>
                <Divider sx={{ mt: 'auto' }} />
                <List>
                    {PLACEHOLDER_LINKS.map(({ text, href, icon: Icon }) => (
                        <ListItem key={href} disablePadding>
                            <ListItemButton component={Link} href={href}>
                                <ListItemIcon>
                                    <Icon />
                                </ListItemIcon>
                                <ListItemText primary={text} />
                            </ListItemButton>
                        </ListItem>
                    ))}
                </List>
            </Drawer>
        </SwipeableDrawer>

        <Box
            component="main"
            sx={{
                flexGrow: 1,
                bgcolor: 'background.default',
                // ml: `${DRAWER_WIDTH}px`,
                pt: ['72px', '80px', '88px'],
                pb: ['48px', '56px', '64px'],
                // p: 3,
                // mt: 3,
                minHeight: "100vh"
            }}
        >
            {children}
        </Box>
    </ThemeProvider>
}
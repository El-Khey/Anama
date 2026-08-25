import { useEffect, useRef, useState } from "react";
import { NavLink, Link, useNavigate } from "react-router-dom";
import {
    ArrowDown01Icon,
    BookOpen01Icon,
    Cancel01Icon,
    Clock01Icon,
    Compass01Icon,
    Home01Icon,
    Bookshelf01Icon,
    Logout01Icon,
    Menu01Icon,
    Notification03Icon,
    Search01Icon,
    Settings02Icon,
    UserIcon,
} from "@hugeicons/core-free-icons";
import type { IconSvgElement } from "@hugeicons/react";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { UserAvatar } from "@/features/profile/components/UserAvatar";
import { Icon } from "@/components/ui/icon";
import { cn } from "@/lib/utils";

interface NavItem {
    label: string;
    to: string;
    end: boolean;
    icon: IconSvgElement;
}

const NAV_ITEMS: NavItem[] = [
    { label: "Accueil", to: "/", end: true, icon: Home01Icon },
    { label: "Explorer", to: "/explorer", end: false, icon: Compass01Icon },
    { label: "Bibliothèque", to: "/novels", end: false, icon: Bookshelf01Icon },
    { label: "Historique", to: "/historique", end: false, icon: Clock01Icon },
];

function navLinkClass({ isActive }: { isActive: boolean }) {
    return cn(
        "inline-flex items-center gap-2 rounded-full px-3.5 py-2 text-sm font-semibold transition-colors",
        isActive
            ? "bg-white/10 text-foreground"
            : "text-muted-foreground hover:bg-white/5 hover:text-foreground",
    );
}

/** Barre de navigation des pages connectées — thème sombre, style « content-first ». */
export default function Navbar() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const [mobileOpen, setMobileOpen] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false);
    const [term, setTerm] = useState("");
    const menuRef = useRef<HTMLDivElement>(null);
    const searchRef = useRef<HTMLInputElement>(null);

    // Ferme le menu compte au clic à l'extérieur.
    useEffect(() => {
        if (!menuOpen) return;
        function onClick(e: MouseEvent) {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
        }
        document.addEventListener("mousedown", onClick);
        return () => document.removeEventListener("mousedown", onClick);
    }, [menuOpen]);

    // Raccourci ⌘K / Ctrl+K : focalise la recherche.
    useEffect(() => {
        function onKey(e: KeyboardEvent) {
            if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
                e.preventDefault();
                searchRef.current?.focus();
            }
        }
        document.addEventListener("keydown", onKey);
        return () => document.removeEventListener("keydown", onKey);
    }, []);

    async function handleLogout() {
        await logout();
        navigate("/login");
    }

    function handleSearch(e: React.FormEvent) {
        e.preventDefault();
        const q = term.trim();
        navigate(q ? `/explorer?q=${encodeURIComponent(q)}` : "/explorer");
        setMobileOpen(false);
    }

    return (
        <header className="sticky top-0 z-40 border-b border-border/70 bg-background/80 backdrop-blur-xl">
            <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6">
                {/* Gauche : marque + nav (desktop) */}
                <div className="flex items-center gap-6">
                    <Link to="/" aria-label="Accueil" className="flex items-center gap-2">
                        <span className="grid size-9 place-items-center rounded-xl bg-linear-to-br from-primary to-primary-active text-primary-foreground shadow-lg shadow-primary/30">
                            <Icon icon={BookOpen01Icon} size={19} strokeWidth={2.4} />
                        </span>
                        <span className="hidden font-heading text-lg font-extrabold tracking-tight sm:block">
                            Novel<span className="text-primary">Realm</span>
                        </span>
                    </Link>
                    <nav className="hidden items-center gap-1 md:flex">
                        {NAV_ITEMS.map((item) => (
                            <NavLink key={item.to} to={item.to} end={item.end} className={navLinkClass}>
                                {item.label}
                            </NavLink>
                        ))}
                    </nav>
                </div>

                {/* Droite : recherche + notifications + compte (desktop) */}
                <div className="flex items-center gap-2">
                    <form onSubmit={handleSearch} className="relative hidden lg:block">
                        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                            <Icon icon={Search01Icon} size={16} />
                        </span>
                        <input
                            ref={searchRef}
                            value={term}
                            onChange={(e) => setTerm(e.target.value)}
                            placeholder="Rechercher un roman…"
                            aria-label="Rechercher"
                            className="h-9 w-60 rounded-full border border-transparent bg-secondary pl-9 pr-12 text-sm text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-ring/60 focus:bg-input"
                        />
                        <kbd className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 rounded border border-border bg-background px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                            ⌘K
                        </kbd>
                    </form>

                    <button
                        type="button"
                        aria-label="Notifications"
                        title="Notifications (bientôt)"
                        className="hidden size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground md:grid"
                    >
                        <Icon icon={Notification03Icon} size={20} />
                    </button>

                    {/* Menu compte */}
                    <div ref={menuRef} className="relative hidden md:block">
                        <button
                            type="button"
                            onClick={() => setMenuOpen((v) => !v)}
                            aria-expanded={menuOpen}
                            className="flex items-center gap-2 rounded-full p-1 pr-2 text-sm transition-colors hover:bg-secondary"
                        >
                            {user && (
                                <UserAvatar
                                    pseudo={user.pseudo}
                                    avatarUrl={user.avatarUrl}
                                    className="size-8"
                                />
                            )}
                            <span className="hidden max-w-28 truncate font-semibold lg:block">
                                {user?.pseudo}
                            </span>
                            <Icon
                                icon={ArrowDown01Icon}
                                size={16}
                                className={cn("text-muted-foreground transition-transform", menuOpen && "rotate-180")}
                            />
                        </button>

                        {menuOpen && (
                            <div className="absolute right-0 top-12 w-56 overflow-hidden rounded-xl border border-border bg-popover p-1.5 shadow-2xl shadow-black/50">
                                <div className="border-b border-border px-3 py-2">
                                    <p className="truncate text-sm font-semibold">{user?.pseudo}</p>
                                    <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
                                </div>
                                <MenuLink to="/profil" icon={UserIcon} onClick={() => setMenuOpen(false)}>
                                    Mon compte
                                </MenuLink>
                                {user?.admin && (
                                    <MenuLink
                                        to="/admin"
                                        icon={Settings02Icon}
                                        onClick={() => setMenuOpen(false)}
                                    >
                                        Admin
                                    </MenuLink>
                                )}
                                <button
                                    type="button"
                                    onClick={handleLogout}
                                    className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
                                >
                                    <Icon icon={Logout01Icon} size={18} />
                                    Se déconnecter
                                </button>
                            </div>
                        )}
                    </div>

                    {/* Burger (mobile) */}
                    <button
                        type="button"
                        onClick={() => setMobileOpen((v) => !v)}
                        aria-label="Menu"
                        aria-expanded={mobileOpen}
                        className="grid size-9 place-items-center rounded-full text-foreground transition-colors hover:bg-secondary md:hidden"
                    >
                        <Icon icon={mobileOpen ? Cancel01Icon : Menu01Icon} size={22} />
                    </button>
                </div>
            </div>

            {/* Panneau mobile */}
            {mobileOpen && (
                <div className="border-t border-border bg-background md:hidden">
                    <div className="mx-auto max-w-7xl space-y-3 px-4 py-4">
                        <form onSubmit={handleSearch} className="relative">
                            <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                                <Icon icon={Search01Icon} size={16} />
                            </span>
                            <input
                                value={term}
                                onChange={(e) => setTerm(e.target.value)}
                                placeholder="Rechercher un roman…"
                                aria-label="Rechercher"
                                className="h-10 w-full rounded-full border border-transparent bg-secondary pl-9 pr-3 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-ring/60 focus:bg-input"
                            />
                        </form>
                        <nav className="flex flex-col gap-1">
                            {NAV_ITEMS.map((item) => (
                                <NavLink
                                    key={item.to}
                                    to={item.to}
                                    end={item.end}
                                    onClick={() => setMobileOpen(false)}
                                    className={({ isActive }) =>
                                        cn(
                                            "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors",
                                            isActive
                                                ? "bg-white/10 text-foreground"
                                                : "text-muted-foreground hover:bg-white/5 hover:text-foreground",
                                        )
                                    }
                                >
                                    <Icon icon={item.icon} size={19} />
                                    {item.label}
                                </NavLink>
                            ))}
                            {user?.admin && (
                                <NavLink
                                    to="/admin"
                                    onClick={() => setMobileOpen(false)}
                                    className={({ isActive }) =>
                                        cn(
                                            "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors",
                                            isActive
                                                ? "bg-white/10 text-foreground"
                                                : "text-muted-foreground hover:bg-white/5 hover:text-foreground",
                                        )
                                    }
                                >
                                    <Icon icon={Settings02Icon} size={19} />
                                    Admin
                                </NavLink>
                            )}
                        </nav>
                        <div className="flex items-center justify-between border-t border-border pt-3">
                            <Link
                                to="/profil"
                                onClick={() => setMobileOpen(false)}
                                className="flex items-center gap-2 text-sm font-semibold"
                            >
                                {user && (
                                    <UserAvatar
                                        pseudo={user.pseudo}
                                        avatarUrl={user.avatarUrl}
                                        className="size-8"
                                    />
                                )}
                                {user?.pseudo}
                            </Link>
                            <button
                                type="button"
                                onClick={handleLogout}
                                className="flex items-center gap-2 rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground"
                            >
                                <Icon icon={Logout01Icon} size={18} />
                                Déconnexion
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </header>
    );
}

function MenuLink({
    to,
    icon,
    children,
    onClick,
}: {
    to: string;
    icon: IconSvgElement;
    children: React.ReactNode;
    onClick: () => void;
}) {
    return (
        <Link
            to={to}
            onClick={onClick}
            className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
        >
            <Icon icon={icon} size={18} />
            {children}
        </Link>
    );
}

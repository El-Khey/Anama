import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "@/features/auth/hooks/useAuth";
import FullPageLoader from "@/components/ui/FullPageLoader";

/**
 * Garde des routes d'administration. Modelé sur {@link ProtectedRoute}, avec un
 * contrôle en plus : réservé aux administrateurs (`user.admin`, renseigné par le
 * back sur `/users/me`). Un non-admin est renvoyé à l'accueil plutôt que vers le
 * login (il EST connecté, il n'a juste pas les droits). Le back reste la source
 * de vérité (403 sur les endpoints) ; ce garde n'est qu'un filtre d'UX.
 */
export default function AdminRoute() {
    const { user, loading } = useAuth();

    if (loading) return <FullPageLoader />;
    if (!user) return <Navigate to="/login" replace />;
    if (!user.admin) return <Navigate to="/" replace />;

    return <Outlet />;
}

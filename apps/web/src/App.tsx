import { Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "@/components/ProtectedRoute";
import AdminRoute from "@/components/AdminRoute";
import LoginPage from "@/pages/LoginPage";
import RegisterPage from "@/pages/RegisterPage";
import HomePage from "@/pages/HomePage";
import ExplorerPage from "@/pages/ExplorerPage";
import LibraryPage from "@/pages/LibraryPage";
import HistoryPage from "@/pages/HistoryPage";
import NovelDetailPage from "@/pages/NovelDetailPage";
import ChapterReaderPage from "@/pages/ChapterReaderPage";
import ProfilPage from "@/pages/ProfilPage";
import AdminPage from "@/pages/AdminPage";

function App() {
    return (
        <Routes>
            {/* Routes publiques — rendues immédiatement, sans attendre la session. */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Routes protégées — ProtectedRoute gère le chargement de la session. */}
            <Route element={<ProtectedRoute />}>
                <Route path="/" element={<HomePage />} />
                <Route path="/explorer" element={<ExplorerPage />} />
                <Route path="/novels" element={<LibraryPage />} />
                <Route path="/historique" element={<HistoryPage />} />
                <Route path="/novels/:id" element={<NovelDetailPage />} />
                <Route path="/novels/:novelId/chapters/:chapterId" element={<ChapterReaderPage />} />
                <Route path="/profil" element={<ProfilPage />} />
            </Route>

            {/* Routes d'administration — réservées aux admins (AdminRoute). */}
            <Route element={<AdminRoute />}>
                <Route path="/admin" element={<AdminPage />} />
            </Route>

            {/* Toute route inconnue ramène à l'accueil. */}
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}

export default App;

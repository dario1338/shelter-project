import { Route, Routes } from "react-router-dom";
import RequireAuth from "./auth/RequireAuth";
import DogDetailsPage from "./pages/DogDetailsPage";
import DogsPage from "./pages/DogsPage";
import HomePage from "./pages/HomePage";
import AdminApplicationsPage from "./pages/AdminApplicationsPage";
import MyApplicationsPage from "./pages/MyApplicationsPage";
import LoginPage from "./pages/LoginPage";
import NotFoundPage from "./pages/NotFoundPage";
import RegisterPage from "./pages/RegisterPage";
import AppLayout from "./ui/AppLayout";

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/dogs" element={<DogsPage />} />
        <Route path="/dogs/:id" element={<DogDetailsPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<RequireAuth role="USER" />}>
          <Route path="/applications" element={<MyApplicationsPage />} />
        </Route>
        <Route element={<RequireAuth role="ADMIN" />}>
          <Route path="/admin/applications" element={<AdminApplicationsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

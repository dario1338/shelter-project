import { Route, Routes } from "react-router-dom";
import RequireAuth from "./auth/RequireAuth";
import DogDetailsPage from "./pages/DogDetailsPage";
import DogsPage from "./pages/DogsPage";
import HomePage from "./pages/HomePage";
import AdminDogCreatePage from "./pages/AdminDogCreatePage";
import AdminDogEditPage from "./pages/AdminDogEditPage";
import AdminApplicationsPage from "./pages/AdminApplicationsPage";
import MyDogsPage from "./pages/MyDogsPage";
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
          <Route path="/my-dogs" element={<MyDogsPage />} />
        </Route>
        <Route element={<RequireAuth role="ADMIN" />}>
          <Route path="/admin/applications" element={<AdminApplicationsPage />} />
          <Route path="/admin/dogs/new" element={<AdminDogCreatePage />} />
          <Route path="/admin/dogs/:id/edit" element={<AdminDogEditPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

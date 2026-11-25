import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { redirectAndroidToPlayStore } from "./services/settingsStorage";
import "./styles.css";

if (!redirectAndroidToPlayStore()) {
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <App />
    </StrictMode>
  );
}

import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { TenantProvider } from './lib/tenant';
import { ActionsPage } from './pages/ActionsPage';
import { CampaignsPage } from './pages/CampaignsPage';
import { InteractionsPage } from './pages/InteractionsPage';
import { OverviewPage } from './pages/OverviewPage';
import { PlaygroundPage } from './pages/PlaygroundPage';

export default function App() {
  return (
    <TenantProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<OverviewPage />} />
            <Route path="actions" element={<ActionsPage />} />
            <Route path="campaigns" element={<CampaignsPage />} />
            <Route path="playground" element={<PlaygroundPage />} />
            <Route path="interactions" element={<InteractionsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </TenantProvider>
  );
}

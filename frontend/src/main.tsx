import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { AppShell } from '@/app/AppShell'
import { TasksPage } from '@/pages/TasksPage'
import { TodayPage } from '@/pages/TodayPage'
import { createQueryClient } from '@/queries/queries'
import './index.css'

const queryClient = createQueryClient()

const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <TodayPage /> },
      { path: 'tasks', element: <TasksPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)

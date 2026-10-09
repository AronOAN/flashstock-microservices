import { Suspense } from 'react';
import AppShell from '@/components/layout/AppShell';
import ReceiptClient from '@/components/receipt/ReceiptClient';

export default function ReceiptPage(){
    return <AppShell><Suspense 
            fallback={<p className="fs-status" role="status">Cargando boleta…</p>}>
            <ReceiptClient/></Suspense>
            </AppShell>;

}

export default function FinancialIntegrityPage() {
  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Financial Integrity</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Payment reconciliation, revenue auditing, and fraud detection
        </p>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8 text-center">
        <div className="w-12 h-12 rounded-full bg-emerald-50 flex items-center justify-center mx-auto mb-4">
          <svg className="h-6 w-6 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <h2 className="text-lg font-semibold text-gray-900 mb-1">Coming Soon</h2>
        <p className="text-sm text-gray-400 max-w-md mx-auto">
          Financial integrity checks including payment reconciliation, duplicate detection, refund auditing, and revenue trend analysis will be available here.
        </p>
      </div>
    </div>
  );
}

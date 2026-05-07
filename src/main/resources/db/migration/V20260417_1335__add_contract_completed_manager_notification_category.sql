ALTER TABLE manager_notifications
    DROP CONSTRAINT IF EXISTS manager_notifications_category_check;

ALTER TABLE manager_notifications
    ADD CONSTRAINT manager_notifications_category_check
    CHECK (category IN (
        'CONTRACT_EXPIRED',
        'INSPECTION_DONE',
        'CONTRACT_READY_FOR_LANDLORD_SIGNATURE',
        'CONTRACT_COMPLETED',
        'RENEWAL_REQUEST',
        'PAYMENT_OVERDUE',
        'DEPOSIT_REFUND_CONFIRM'
    ));

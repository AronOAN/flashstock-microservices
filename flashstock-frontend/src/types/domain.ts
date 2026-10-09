/**
 * Browser-facing transport contracts only. Java is authoritative for all values.
 * No frontend DTO is a security control. Validate server data at trust boundaries.
 */
export type SessionUser = {
  authenticated: boolean;
  admin: boolean; // UI hint ONLY; backend must authorize every protected action.
  email: string | null;
  displayName: string;
  authorities: string[];
  scopes?: string[];
  permissions?: string[];
  // Add a stable public user ID only once Java actually includes it in /api/auth/me.
};

export type CatalogItem = {
  sku: string;
  name: string;
  description?: string | null;
  unitPrice: number;
  imageUrl?: string | null;
  category?: string | null;
  availableQuantity: number;
};

export type InventoryItem = {
  id: number;
  sku: string;
  supplierId?: number | null;
  name: string;
  description?: string | null;
  unitPrice: number;
  price?: number | null;
  imageUrl?: string | null;
  category?: string | null;
  quantity: number; // Available to sell according to InventoryResponse
  stock: number; // Physical stock according to InventoryResponse
  warehouse?: string | null;
  active: boolean;
};

export type CartItem = {
  inventoryId: number;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number; // Display only; Order backend must derive official price.
  imageUrl?: string | null;
  availableQuantity: number;
};

export type OrderHistoryItem = {
  orderId: number;
  orderNumber: string;
  orderStatus: string;
  shipmentTrackingNumber?: string | null;
  shipmentStatus?: string | null;
  carrier?: string | null;
  customerFirstName?: string | null;
  customerLastName?: string | null;
  customerEmail?: string | null;
  shippingAddress?: string | null;
};

export type ShipmentTracking = {
  trackingNumber: string;
  orderNumber: string;
  orderStatus: string;
  shipmentStatus: string;
  shippingAddress?: string | null;
  courierName?: string | null;
  progressPercent?: number | null;
  totalDurationText?: string | null;
  remainingDurationText?: string | null;
  lastUpdate?: string | null;
};

export type ShipmentAdminItem = {
  trackingNumber: string;
  orderNumber?: string | null;
  carrier?: string | null;
  courierName?: string | null;
  status?: string | null;
  eta?: string | null;
};

export type AdminMetrics = {
  timestamp?: string | null;
  inventorySkuCount?: number | null;
  totalStock?: number | null;
  lowRiskSkuCount?: number | null;
  criticalRiskSkuCount?: number | null;
  totalOrders?: number | null;
  createdOrders?: number | null;
  completedOrders?: number | null;
  cancelledOrders?: number | null;
  totalShipments?: number | null;
  preparingShipments?: number | null;
  inTransitShipments?: number | null;
  deliveredShipments?: number | null;
  grossCashflow?: number | null; // NOT necessarily actual revenue in current Java service.
  realizedCashflow?: number | null;
  pendingCashflow?: number | null;
};

export type Receipt = {
  receiptNumber: string;
  createdAt?: string | null;
  customerEmail: string;
  customerFirstName?: string | null;
  customerLastName?: string | null;
  shippingAddress?: string | null;
  subtotal: number;
  shipping: number;
  discount: number;
  total: number;
  items?: Array<{
    sku?: string;
    productName?: string;
    quantity?: number;
    unitPrice?: number;
    lineTotal?: number;
  }>;
  shipments?: Array<{
    orderNumber?: string;
    trackingNumber?: string;
    carrier?: string;
    courierName?: string;
    status?: string;
    eta?: string;
  }>;
};

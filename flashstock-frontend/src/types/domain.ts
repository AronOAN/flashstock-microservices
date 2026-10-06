export type SessionUser = {
  authenticated: boolean;
  admin: boolean;
  email: string | null;
  displayName: string;
  authorities: string[];
  scopes?: string[];
  permissions?: string[];
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
  quantity: number;
  stock: number;
  warehouse?: string | null;
  active: boolean;
};

export type CartItem = {
  inventoryId: number;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
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
  items?: Array<{sku?:string; productName?:string; quantity?:number; unitPrice?:number; lineTotal?:number}>;
  shipments?: Array<{orderNumber?:string; trackingNumber?:string; carrier?:string; courierName?:string; status?:string; eta?:string}>;
};

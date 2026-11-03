export interface Ref {
  id: number;
  name: string;
}

export interface Customer {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  active: boolean;
  version: number;
  orderCount: number;
}

export interface CustomerInput {
  name: string;
  phone: string;
  email: string;
  address: string;
  active: boolean;
  version?: number;
}

export interface Fabric {
  id: number;
  code: string;
  name: string;
  material: string | null;
  colour: string | null;
  pattern: string | null;
  width: number | null;
  costPerMetre: number;
  pricePerMetre: number;
  active: boolean;
  version: number;
  quantityOnHand: number;
  reorderLevel: number;
  lowStock: boolean;
}

export interface FabricInput {
  code: string;
  name: string;
  material: string;
  colour: string;
  pattern: string;
  width: number | null;
  costPerMetre: number | null;
  pricePerMetre: number | null;
  reorderLevel: number | null;
  active: boolean;
  version?: number;
}

export type GarmentShape = "TOP" | "BOTTOM" | "FULL";

export interface GarmentPoint {
  id: number;
  label: string;
  sortOrder: number;
  active: boolean;
}

export interface GarmentType {
  id: number;
  name: string;
  description: string | null;
  shape: GarmentShape;
  active: boolean;
  version: number;
  inUse: boolean;
  points: GarmentPoint[];
}

export interface GarmentPointInput {
  id: number | null;
  label: string;
  sortOrder: number;
  active: boolean;
}

export interface GarmentTypeInput {
  name: string;
  description: string;
  shape: GarmentShape;
  active: boolean;
  points: GarmentPointInput[];
  version?: number;
}

export interface MeasurementValue {
  pointId: number;
  label: string;
  value: number;
  sortOrder: number;
}

export interface MeasurementRecord {
  id: number;
  customerId: number;
  customerName: string;
  garmentType: { id: number; name: string; shape: GarmentShape };
  takenOn: string;
  notes: string | null;
  createdAt: string;
  createdBy: string;
  values: MeasurementValue[];
}

export interface MeasurementRecordInput {
  garmentTypeId: number;
  takenOn: string;
  notes: string;
  values: { pointId: number; value: number }[];
}

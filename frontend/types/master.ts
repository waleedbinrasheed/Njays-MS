export interface Ref {
  id: number;
  name: string;
}

interface Audited {
  id: number;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Publishers and suppliers share this shape. */
export interface Party extends Audited {
  name: string;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
}

export interface PartyInput {
  name: string;
  contactPerson: string;
  phone: string;
  email: string;
  address: string;
  active: boolean;
  version?: number;
}

export interface Customer extends Audited {
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  walkIn: boolean;
}

export interface CustomerInput {
  name: string;
  phone: string;
  email: string;
  address: string;
  active: boolean;
  version?: number;
}

export interface Category extends Audited {
  name: string;
  description: string | null;
}

export interface CategoryInput {
  name: string;
  description: string;
  active: boolean;
  version?: number;
}

export interface Product extends Audited {
  productType: "BOOK";
  isbn: string | null;
  title: string;
  author: string | null;
  publisher: Ref | null;
  category: Ref | null;
  edition: string | null;
  publicationYear: number | null;
  purchasePrice: number;
  sellingPrice: number;
}

export interface ProductInput {
  isbn: string;
  title: string;
  author: string;
  publisherId: number | null;
  categoryId: number | null;
  edition: string;
  publicationYear: number | null;
  purchasePrice: number | null;
  sellingPrice: number | null;
  active: boolean;
  version?: number;
}

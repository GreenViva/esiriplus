"use client";

import { createContext, useContext } from "react";
import type { PortalRole } from "@/lib/types/database";

const RoleContext = createContext<PortalRole>("admin");

export const RoleProvider = RoleContext.Provider;

export function useRole(): PortalRole {
  return useContext(RoleContext);
}

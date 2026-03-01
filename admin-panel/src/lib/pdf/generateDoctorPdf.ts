import jsPDF from "jspdf";
import autoTable from "jspdf-autotable";
import { specialtyLabel, formatDate } from "@/lib/utils";

// Brand colors
const BRAND_TEAL: [number, number, number] = [42, 157, 143];
const DARK_TEXT: [number, number, number] = [17, 24, 39];
const LIGHT_TEXT: [number, number, number] = [107, 114, 128];
const BORDER_COLOR: [number, number, number] = [229, 231, 235];
const WHITE: [number, number, number] = [255, 255, 255];

const PAGE_MARGIN = 15;
const CONTENT_WIDTH = 180; // A4 (210) - 2*15

const PDFJS_CDN_VERSION = "4.9.155";
const PDFJS_CDN_BASE = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${PDFJS_CDN_VERSION}`;

export interface DoctorPdfInput {
  doctor_id: string;
  full_name: string;
  email: string;
  phone?: string | null;
  specialty: string;
  specialist_field?: string | null;
  languages?: string[];
  bio?: string | null;
  license_number?: string | null;
  years_experience?: number | null;
  profile_photo_url?: string | null;
  is_verified?: boolean;
  country?: string | null;
  country_code?: string | null;
  services?: string | null;
  license_document_url?: string | null;
  certificates_url?: string | null;
  rejection_reason?: string | null;
  created_at?: string;
  average_rating?: number;
  total_ratings?: number;
}

interface FetchedImage {
  dataUrl: string;
  width: number;
  height: number;
}

/** Detect content type via HEAD request, fall back to URL-based guessing */
async function detectContentType(url: string): Promise<"image" | "pdf" | "unknown"> {
  try {
    const res = await fetch(url, { method: "HEAD" });
    const ct = res.headers.get("content-type")?.toLowerCase() ?? "";
    if (ct.startsWith("image/")) return "image";
    if (ct === "application/pdf") return "pdf";
  } catch { /* fall through to URL guessing */ }

  const lower = url.toLowerCase();
  if (
    lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
    lower.endsWith(".png") || lower.endsWith(".webp") ||
    lower.includes("profile-photo") || lower.includes("image")
  ) return "image";
  if (lower.endsWith(".pdf")) return "pdf";
  return "unknown";
}

async function fetchImage(url: string): Promise<FetchedImage | null> {
  try {
    return await new Promise<FetchedImage | null>((resolve) => {
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        const canvas = document.createElement("canvas");
        canvas.width = img.naturalWidth;
        canvas.height = img.naturalHeight;
        const ctx = canvas.getContext("2d");
        if (!ctx) { resolve(null); return; }
        ctx.drawImage(img, 0, 0);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
        resolve({ dataUrl, width: img.naturalWidth, height: img.naturalHeight });
      };
      img.onerror = () => resolve(null);
      img.src = url;
    });
  } catch {
    return null;
  }
}

/** Load pdf.js from CDN dynamically (bypasses webpack ESM bundling issues) */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loadPdfJs(): Promise<any> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  if ((window as any).pdfjsLib) return (window as any).pdfjsLib;

  // Use new Function to create a real import() that webpack can't intercept
  const dynamicImport = new Function("url", "return import(url)");
  await dynamicImport(`${PDFJS_CDN_BASE}/pdf.min.mjs`);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const lib = (window as any).pdfjsLib;
  if (!lib) throw new Error("Failed to load pdf.js from CDN");

  lib.GlobalWorkerOptions.workerSrc = `${PDFJS_CDN_BASE}/pdf.worker.min.mjs`;
  return lib;
}

/** Render all pages of a remote PDF as JPEG images */
async function fetchPdfAsImages(url: string): Promise<FetchedImage[]> {
  try {
    const pdfjsLib = await loadPdfJs();
    const response = await fetch(url);
    const arrayBuffer = await response.arrayBuffer();
    const pdfDoc = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
    const images: FetchedImage[] = [];

    for (let i = 1; i <= pdfDoc.numPages; i++) {
      const page = await pdfDoc.getPage(i);
      const scale = 2; // 2x for good quality
      const viewport = page.getViewport({ scale });
      const canvas = document.createElement("canvas");
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      const ctx = canvas.getContext("2d");
      if (!ctx) continue;

      await page.render({ canvasContext: ctx, viewport }).promise;
      const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
      images.push({ dataUrl, width: viewport.width, height: viewport.height });
    }

    return images;
  } catch (err) {
    console.error("Failed to render PDF:", url, err);
    return [];
  }
}

/** Fetch a credential: detect type via Content-Type header, then fetch accordingly */
async function fetchCredential(url: string): Promise<{ images: FetchedImage[]; type: "image" | "pdf" | "failed" }> {
  const contentType = await detectContentType(url);

  if (contentType === "image") {
    const img = await fetchImage(url);
    return img ? { images: [img], type: "image" } : { images: [], type: "failed" };
  }
  if (contentType === "pdf") {
    const pages = await fetchPdfAsImages(url);
    return pages.length > 0 ? { images: pages, type: "pdf" } : { images: [], type: "failed" };
  }
  // Unknown type — try as image first, then PDF
  const img = await fetchImage(url);
  if (img) return { images: [img], type: "image" };
  const pages = await fetchPdfAsImages(url);
  if (pages.length > 0) return { images: pages, type: "pdf" };
  return { images: [], type: "failed" };
}

function scaleToFit(
  imgW: number, imgH: number,
  maxW: number, maxH: number,
): { w: number; h: number } {
  const ratio = Math.min(maxW / imgW, maxH / imgH, 1);
  return { w: imgW * ratio, h: imgH * ratio };
}

export async function generateDoctorPdf(doctor: DoctorPdfInput): Promise<void> {
  // Fetch all credentials in parallel (images + PDF documents rendered as images)
  const [photoResult, licenseResult, certsResult] = await Promise.all([
    doctor.profile_photo_url ? fetchCredential(doctor.profile_photo_url) : null,
    doctor.license_document_url ? fetchCredential(doctor.license_document_url) : null,
    doctor.certificates_url ? fetchCredential(doctor.certificates_url) : null,
  ]);

  // Profile photo is the first image from the photo credential (for header use)
  const photoImg = photoResult?.images[0] ?? null;

  const doc = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });
  const pageH = doc.internal.pageSize.getHeight();
  let y = 0;

  function addFooter() {
    const pages = doc.getNumberOfPages();
    for (let i = 1; i <= pages; i++) {
      doc.setPage(i);
      doc.setFontSize(7);
      doc.setTextColor(...LIGHT_TEXT);
      doc.text("eSIRI+ Admin Panel - Confidential", PAGE_MARGIN, pageH - 8);
      doc.text(`Page ${i} of ${pages}`, 210 - PAGE_MARGIN, pageH - 8, { align: "right" });
    }
  }

  function ensureSpace(needed: number) {
    if (y + needed > pageH - PAGE_MARGIN - 12) {
      doc.addPage();
      y = PAGE_MARGIN;
    }
  }

  function sectionHeader(title: string) {
    ensureSpace(14);
    y += 4;
    doc.setFontSize(12);
    doc.setTextColor(...BRAND_TEAL);
    doc.setFont("helvetica", "bold");
    doc.text(title, PAGE_MARGIN, y);
    y += 2;
    doc.setDrawColor(...BRAND_TEAL);
    doc.setLineWidth(0.5);
    doc.line(PAGE_MARGIN, y, PAGE_MARGIN + CONTENT_WIDTH, y);
    y += 5;
  }

  // ─── HEADER ───
  doc.setFillColor(...BRAND_TEAL);
  doc.rect(0, 0, 210, 38, "F");

  doc.setTextColor(...WHITE);
  doc.setFontSize(22);
  doc.setFont("helvetica", "bold");
  doc.text("eSIRI+", PAGE_MARGIN, 16);

  doc.setFontSize(11);
  doc.setFont("helvetica", "normal");
  doc.text("Doctor Application Report", PAGE_MARGIN, 24);

  doc.setFontSize(9);
  doc.text(`Generated: ${new Date().toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })}`, PAGE_MARGIN, 31);

  // Status badge
  const status = doctor.is_verified ? "Approved" : doctor.rejection_reason ? "Rejected" : "Pending Verification";
  const statusColor: [number, number, number] = doctor.is_verified ? [34, 197, 94] : doctor.rejection_reason ? [239, 68, 68] : [245, 158, 11];
  doc.setFillColor(...statusColor);
  const statusWidth = doc.getTextWidth(status) + 10;
  doc.roundedRect(210 - PAGE_MARGIN - statusWidth, 10, statusWidth, 8, 2, 2, "F");
  doc.setFontSize(8);
  doc.setFont("helvetica", "bold");
  doc.setTextColor(...WHITE);
  doc.text(status, 210 - PAGE_MARGIN - statusWidth + 5, 15.5);

  y = 46;

  // ─── PERSONAL INFORMATION ───
  sectionHeader("Personal Information");

  const photoX = PAGE_MARGIN;
  let tableStartX = PAGE_MARGIN;

  // Embed profile photo to the left if available
  if (photoImg) {
    const { w, h } = scaleToFit(photoImg.width, photoImg.height, 35, 35);
    doc.addImage(photoImg.dataUrl, "JPEG", photoX, y, w, h);
    tableStartX = photoX + 40;
  }

  const personalRows: [string, string][] = [
    ["Full Name", doctor.full_name],
    ["Email", doctor.email],
  ];
  if (doctor.phone) personalRows.push(["Phone", `${doctor.country_code ?? ""} ${doctor.phone}`]);
  if (doctor.country) personalRows.push(["Country", doctor.country]);

  autoTable(doc, {
    startY: y,
    margin: { left: tableStartX },
    tableWidth: CONTENT_WIDTH - (tableStartX - PAGE_MARGIN),
    theme: "plain",
    styles: { fontSize: 9, cellPadding: { top: 1.5, bottom: 1.5, left: 2, right: 2 } },
    columnStyles: {
      0: { fontStyle: "bold", textColor: LIGHT_TEXT, cellWidth: 35 },
      1: { textColor: DARK_TEXT },
    },
    body: personalRows,
  });

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  y = Math.max((doc as any).lastAutoTable.finalY + 4, photoImg ? y + 38 : y);

  // ─── PROFESSIONAL DETAILS ───
  sectionHeader("Professional Details");

  const profRows: [string, string][] = [
    ["Specialty", specialtyLabel(doctor.specialty)],
  ];
  if (doctor.specialist_field) profRows.push(["Specialist Field", doctor.specialist_field]);
  if (doctor.license_number) profRows.push(["License Number", doctor.license_number]);
  if (doctor.years_experience != null) profRows.push(["Experience", `${doctor.years_experience} years`]);
  if (doctor.languages?.length) profRows.push(["Languages", doctor.languages.join(", ")]);
  if (doctor.services) {
    try {
      const arr = JSON.parse(doctor.services);
      if (Array.isArray(arr)) profRows.push(["Services", arr.join(", ")]);
      else profRows.push(["Services", doctor.services]);
    } catch {
      profRows.push(["Services", doctor.services]);
    }
  }
  if (doctor.average_rating != null) profRows.push(["Rating", `${doctor.average_rating.toFixed(1)} (${doctor.total_ratings ?? 0} reviews)`]);

  autoTable(doc, {
    startY: y,
    margin: { left: PAGE_MARGIN },
    tableWidth: CONTENT_WIDTH,
    theme: "plain",
    styles: { fontSize: 9, cellPadding: { top: 1.5, bottom: 1.5, left: 2, right: 2 } },
    columnStyles: {
      0: { fontStyle: "bold", textColor: LIGHT_TEXT, cellWidth: 40 },
      1: { textColor: DARK_TEXT },
    },
    body: profRows,
  });

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  y = (doc as any).lastAutoTable.finalY + 4;

  // ─── BIO ───
  if (doctor.bio) {
    sectionHeader("Bio");
    doc.setFontSize(9);
    doc.setFont("helvetica", "normal");
    doc.setTextColor(...DARK_TEXT);
    const lines = doc.splitTextToSize(doctor.bio, CONTENT_WIDTH);
    ensureSpace(lines.length * 4 + 4);
    doc.text(lines, PAGE_MARGIN, y);
    y += lines.length * 4 + 4;
  }

  // ─── CREDENTIALS ───
  sectionHeader("Credentials & Documents");

  const credentials: { label: string; url: string | null | undefined; result: Awaited<ReturnType<typeof fetchCredential>> | null }[] = [
    { label: "Profile Photo", url: doctor.profile_photo_url, result: photoResult },
    { label: "Medical License", url: doctor.license_document_url, result: licenseResult },
    { label: "Certificates", url: doctor.certificates_url, result: certsResult },
  ];

  let hasAnyCred = false;

  for (const cred of credentials) {
    if (!cred.url) continue;
    hasAnyCred = true;

    ensureSpace(12);
    doc.setFontSize(10);
    doc.setFont("helvetica", "bold");
    doc.setTextColor(...DARK_TEXT);
    doc.text(cred.label, PAGE_MARGIN, y);
    y += 5;

    const images = cred.result?.images ?? [];

    if (images.length > 0) {
      // Embed all images (single image for photos, multiple pages for PDFs)
      if (cred.result?.type === "pdf" && images.length > 1) {
        doc.setFontSize(8);
        doc.setFont("helvetica", "normal");
        doc.setTextColor(...LIGHT_TEXT);
        doc.text(`(${images.length} pages)`, PAGE_MARGIN + doc.getTextWidth(cred.label) + 4, y - 5);
      }

      for (let i = 0; i < images.length; i++) {
        const img = images[i];
        const maxImgH = 120;
        const { w, h } = scaleToFit(img.width, img.height, CONTENT_WIDTH, maxImgH);
        ensureSpace(h + 8);

        if (cred.result?.type === "pdf" && images.length > 1) {
          doc.setFontSize(7);
          doc.setFont("helvetica", "normal");
          doc.setTextColor(...LIGHT_TEXT);
          doc.text(`Page ${i + 1} of ${images.length}`, PAGE_MARGIN, y);
          y += 3;
        }

        // Light border around image
        doc.setDrawColor(...BORDER_COLOR);
        doc.setLineWidth(0.3);
        doc.rect(PAGE_MARGIN, y, w, h);
        doc.addImage(img.dataUrl, "JPEG", PAGE_MARGIN, y, w, h);
        y += h + 4;
      }
      y += 2;
    } else {
      // Failed to fetch — show as clickable link fallback
      ensureSpace(10);
      doc.setFontSize(8);
      doc.setFont("helvetica", "normal");
      doc.setTextColor(...LIGHT_TEXT);
      doc.text("Could not embed document — click link to view:", PAGE_MARGIN, y);
      y += 4;
      doc.setTextColor(...BRAND_TEAL);
      doc.textWithLink(cred.url, PAGE_MARGIN, y, { url: cred.url });
      y += 8;
    }
  }

  if (!hasAnyCred) {
    doc.setFontSize(9);
    doc.setFont("helvetica", "italic");
    doc.setTextColor(...LIGHT_TEXT);
    doc.text("No credentials uploaded", PAGE_MARGIN, y);
    y += 8;
  }

  // ─── VERIFICATION STATUS ───
  sectionHeader("Verification Status");

  const statusRows: [string, string][] = [
    ["Status", status],
  ];
  if (doctor.rejection_reason) statusRows.push(["Rejection Reason", doctor.rejection_reason]);
  if (doctor.created_at) statusRows.push(["Application Date", formatDate(doctor.created_at)]);

  autoTable(doc, {
    startY: y,
    margin: { left: PAGE_MARGIN },
    tableWidth: CONTENT_WIDTH,
    theme: "plain",
    styles: { fontSize: 9, cellPadding: { top: 1.5, bottom: 1.5, left: 2, right: 2 } },
    columnStyles: {
      0: { fontStyle: "bold", textColor: LIGHT_TEXT, cellWidth: 40 },
      1: { textColor: DARK_TEXT },
    },
    body: statusRows,
  });

  // Add footers to all pages
  addFooter();

  // Save
  const safeName = doctor.full_name.replace(/[^a-zA-Z0-9 ]/g, "").replace(/\s+/g, "_");
  doc.save(`Dr_${safeName}_Application.pdf`);
}

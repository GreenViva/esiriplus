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

function isImageUrl(url: string): boolean {
  const lower = url.toLowerCase();
  return (
    lower.endsWith(".jpg") ||
    lower.endsWith(".jpeg") ||
    lower.endsWith(".png") ||
    lower.endsWith(".webp") ||
    lower.includes("profile-photo") ||
    lower.includes("image")
  );
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

function scaleToFit(
  imgW: number, imgH: number,
  maxW: number, maxH: number,
): { w: number; h: number } {
  const ratio = Math.min(maxW / imgW, maxH / imgH, 1);
  return { w: imgW * ratio, h: imgH * ratio };
}

export async function generateDoctorPdf(doctor: DoctorPdfInput): Promise<void> {
  // Fetch all credential images in parallel
  const [photoImg, licenseImg, certsImg] = await Promise.all([
    doctor.profile_photo_url && isImageUrl(doctor.profile_photo_url)
      ? fetchImage(doctor.profile_photo_url) : null,
    doctor.license_document_url && isImageUrl(doctor.license_document_url)
      ? fetchImage(doctor.license_document_url) : null,
    doctor.certificates_url && isImageUrl(doctor.certificates_url)
      ? fetchImage(doctor.certificates_url) : null,
  ]);

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

  const credentials: { label: string; url: string | null | undefined; img: FetchedImage | null }[] = [
    { label: "Profile Photo", url: doctor.profile_photo_url, img: photoImg },
    { label: "Medical License", url: doctor.license_document_url, img: licenseImg },
    { label: "Certificates", url: doctor.certificates_url, img: certsImg },
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

    if (cred.img) {
      // Embed image
      const maxImgH = 90;
      const { w, h } = scaleToFit(cred.img.width, cred.img.height, CONTENT_WIDTH, maxImgH);
      ensureSpace(h + 6);

      // Light border around image
      doc.setDrawColor(...BORDER_COLOR);
      doc.setLineWidth(0.3);
      doc.rect(PAGE_MARGIN, y, w, h);
      doc.addImage(cred.img.dataUrl, "JPEG", PAGE_MARGIN, y, w, h);
      y += h + 6;
    } else {
      // PDF or failed fetch — show as link
      ensureSpace(10);
      doc.setFontSize(8);
      doc.setFont("helvetica", "normal");

      const isPdf = cred.url.toLowerCase().includes("pdf");
      doc.setTextColor(...LIGHT_TEXT);
      doc.text(isPdf ? "PDF Document" : "Document", PAGE_MARGIN, y);
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

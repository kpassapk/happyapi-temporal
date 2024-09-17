(ns happyapi.temporal.sheets-v4)

(defn spreadsheets-get
  "Returns the spreadsheet at the given ID. The caller must specify the spreadsheet ID. By default, data within grids is not returned. You can include grid data in one of 2 ways: * Specify a [field mask](https://developers.google.com/sheets/api/guides/field-masks) listing your desired fields using the `fields` URL parameter in HTTP * Set the includeGridData URL parameter to true. If a field mask is set, the `includeGridData` parameter is ignored For large spreadsheets, as a best practice, retrieve only the specific spreadsheet fields that you want. To retrieve only subsets of spreadsheet data, use the ranges URL parameter. Ranges are specified using [A1 notation](/sheets/api/guides/concepts#cell). You can define a single cell (for example, `A1`) or multiple cells (for example, `A1:D5`). You can also get cells from other sheets within the same spreadsheet (for example, `Sheet2!A1:C4`) or retrieve multiple ranges at once (for example, `?ranges=A1:D5&ranges=Sheet2!A1:C4`). Limiting the range returns only the portions of the spreadsheet that intersect the requested ranges.
https://developers.google.com/sheets/v4/reference/rest/v4/spreadsheets/get

spreadsheetId <>

optional:
ranges <string> The ranges to retrieve from the spreadsheet.
includeGridData <boolean> True if grid data should be returned. This parameter is ignored if a field mask was set in the request."
  ([spreadsheetId] (spreadsheets-get spreadsheetId nil))
  ([spreadsheetId optional]
    {:method :get,
     :uri-template
     "https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}",
     :uri-template-args {"spreadsheetId" spreadsheetId},
     :query-params (merge {} optional),
     :scopes
     ["https://www.googleapis.com/auth/drive"
      "https://www.googleapis.com/auth/drive.file"
      "https://www.googleapis.com/auth/drive.readonly"
      "https://www.googleapis.com/auth/spreadsheets"
      "https://www.googleapis.com/auth/spreadsheets.readonly"]}))

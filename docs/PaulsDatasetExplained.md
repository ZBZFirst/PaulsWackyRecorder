# Workbook Validation Overview

**File:** `PaulsUSDataset.xlsx`

## Current App Integration Status

The Paulsdataset.xlsx contains the rules that the app should use for columns mapped to a pre defined column bank.
## Purpose of the Workbook

The workbook `Paulsdataset.xlsx` operates as a layered validation system in which each sheet has a specific responsibility. The sheets work together to allow the user to determine whether each cell in the dataset matches the rules assigned to its column. The core idea is that the raw data does not validate itself. Instead, the workbook uses metadata, reusable regex patterns, and validation formulas to decide what each value is supposed to look like and whether it passes.

## Intro Sheet

The intro sheet has two columns [STEPS_FOR_INGESTING,	STEP_DESCRIPTION] which describe the process that SHOULD be used for ingesting the sheet reliably and easily.

## RowMetaDatat Sheet

This sheet contains the following columns [row,	row_kind,	sheet_usage,	expected_usage,	note]. These explain the purpose of each row in the Sample Dataset.

## ColumnMetaData Sheet

This sheet acts as the schema definition for the workbook. It is composed of the following columns [group,	group_classification,	column_name,	data_type,	regex_pattern_name,	example_value,	min,	max,	allowed_values,	normalize,	notes,	participates_in_validation]. This sheet explains how each column should be interpreted by using mapping the sample datasets column headers to rows in this sheet to explain the columns attributes through this sheets columns. For each column name, it can define the expected type, which regex pattern applies, what normalization is needed, and whether there is a fixed set of allowed values. The logic is not hardcoded for specific spreadsheet positions; instead, formulas can look up the current column header and then retrieve that column’s rules from this metadata sheet.

## SampleDataset Sheet

The raw data in a finished format. This was generated programatically using the python library FAKER. This is what we can use to test the Meta Data Logic.

There is a column for 100 predefined column types. These include the following in the SampleDataset as columns.

The list is space separated.

[uuid4	name	first_name	last_name	prefix	suffix	user_name	job	company	company_suffix	email	ascii_email	company_email	phone_number	basic_phone_number	address	street_address	street_name	building_number	city	city_prefix	city_suffix	state	state_abbr	country	country_code	zipcode	postalcode	domain_name	domain_word	url	uri	uri_path	uri_page	uri_extension	ipv4	ipv6	mac_address	hostname	user_agent	credit_card_number	credit_card_provider	credit_card_expire	credit_card_security_code	currency_code	currency_name	currency_symbol	latitude	longitude	coordinate	word	words	sentence	paragraph	slug	text	color_name	hex_color	mime_type	file_name	file_extension	date_mdy_slash_yyyy	date_mdy_dash_yyyy	date_mdy_slash_yy	date_mdy_dash_yy	timestamp_mdy_slash_minute	timestamp_mdy_dash_minute	timestamp_mdy_slash_second	timestamp_mdy_dash_second	time_hh_mm	time_hh_mm_ss	time_hh_mm_ss_mmm	time_hh_mm_am_pm	time_hh_mm_ss_am_pm	time_hh_mm_ss_mmm_am_pm	timestamp_unix_s	timestamp_unix_ms	year_yyyy	year_yy	month_mm	day_dd	day_of_week	decimal_1	decimal_2	decimal_3	decimal_grouped_2	ones	tens	hundreds	thousands	ten_thousands	hundred_thousands	millions	number_plain	number_grouped	number_scientific	currency_usd	currency_usd_plain	percent	percent_decimal	date_separator_slash	date_separator_dash	grouping_separator	decimal_separator	fraction_separator]

As a result, the ColumnMetaData sheet contains a row for each of these columns with their predefined meta data.

## RegexPatterns Sheet

This sheet stores reusable named regex definitions. It is composed of the following columns [regex_pattern_name,	regex,	description]. Instead of writing a full regex expression repeatedly in many places, the workbook can assign a pattern name to a column in `ColumnMetaData`, then retrieve the actual regex from RegexPatterns. This creates consistency. For example, multiple columns might reuse patterns for email format, IPv4 format, hexadecimal color codes, or U.S. state abbreviations. This sheet functions as the workbook’s pattern library.

## NormalizationRules Sheet

The `ValidationRules` sheet complements regex matching by describing additional cleanup or normalization behavior. It has the columns [NormalizationProcedure,	Operation,	Pattern,	Replacement,	Description]. A value may need to be trimmed, uppercased, or stripped of punctuation before evaluation. This matters because a cell can be logically correct but formatted inconsistently. Normalization allows the workbook to clean the value first, then validate the normalized result. That keeps validation stricter in logic while remaining tolerant of superficial formatting noise.

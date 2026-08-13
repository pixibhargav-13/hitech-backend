-------------------------------------------------
-- Item photo.
--
-- Vyapar's Add Item form carries "Add Item Image"; ours rendered the button and then told the user
-- the feature didn't exist. Stored inline as a data URL, downscaled client-side, matching how the
-- firm profile already keeps its logo (firm_profiles.logo_data_url) rather than standing up file
-- storage for a thumbnail.
-------------------------------------------------
ALTER TABLE vyapar_items
    ADD COLUMN IF NOT EXISTS image_data_url TEXT;

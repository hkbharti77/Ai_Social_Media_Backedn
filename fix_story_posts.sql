-- ============================================
-- Fix Story Posts Without Images
-- ============================================
-- This script helps identify and fix story posts that are missing images

-- 1. Find all story posts without images (PROBLEMATIC POSTS)
SELECT 
    id, 
    caption, 
    platform, 
    status, 
    is_story,
    image_url,
    scheduled_at,
    created_at
FROM posts 
WHERE is_story = true 
  AND (image_url IS NULL OR image_url = '')
ORDER BY id;

-- 2. OPTION A: Delete problematic story posts (RECOMMENDED for stuck scheduled posts)
-- Uncomment the line below to delete posts 126 and similar
-- DELETE FROM posts WHERE id IN (126, 128) AND is_story = true AND (image_url IS NULL OR image_url = '');

-- 3. OPTION B: Convert to draft status (if you want to keep them for editing)
-- Uncomment the lines below to convert to draft
-- UPDATE posts 
-- SET status = 'DRAFT', 
--     scheduled_at = NULL,
--     failure_reason = 'Story requires an image. Please add an image and reschedule.'
-- WHERE is_story = true 
--   AND (image_url IS NULL OR image_url = '')
--   AND status = 'SCHEDULED';

-- 4. OPTION C: Mark as failed with clear reason
-- Uncomment the lines below to mark as failed
-- UPDATE posts 
-- SET status = 'FAILED',
--     failure_reason = 'Story posts require an image. This post was created without an image and cannot be published.'
-- WHERE is_story = true 
--   AND (image_url IS NULL OR image_url = '')
--   AND status IN ('SCHEDULED', 'DRAFT');

-- 5. Verify the fix
-- Run this after applying any of the above options
SELECT 
    status,
    COUNT(*) as count
FROM posts 
WHERE is_story = true 
  AND (image_url IS NULL OR image_url = '')
GROUP BY status;

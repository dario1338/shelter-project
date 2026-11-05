import { Heading, Link, Stack } from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <Stack gap="4">
      <Heading as="h1" size="2xl">
        Page not found
      </Heading>
      <Link asChild variant="underline">
        <RouterLink to="/">Go to the home page</RouterLink>
      </Link>
    </Stack>
  );
}

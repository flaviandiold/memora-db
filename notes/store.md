# Memora Store

This is where the actual data is stored, a key value pair in the store is stored like this.

{
    "key" : {
        "value": string,
        "version": long,
        "ttl": long
    }
}

Here the version is a int, where the version is increased for every mutation. When we delete the key from the primary the key will be deleted from itself.